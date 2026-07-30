/**
 * Gốc của API. Để trống khi chạy dev (Vite proxy /api sang localhost:8080) và khi frontend
 * được phục vụ cùng origin với backend. Khi deploy tách rời — ví dụ frontend trên Cloudflare
 * Pages, backend ở nơi khác — đặt VITE_API_BASE_URL lúc build, vd:
 * VITE_API_BASE_URL=https://api.wearwise.example.com
 */
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/+$/, '');

export function apiUrl(path) {
  return `${API_BASE_URL}${path}`;
}

/**
 * Header tự đặt, gửi kèm MỌI request. Backend chỉ chấp nhận cookie refresh token khi thấy
 * header này — đó là lớp chống CSRF: trang web lạ không đặt được header tự chế (form HTML
 * không có cơ chế đó, còn fetch từ origin lạ thì vấp CORS preflight).
 */
export const CLIENT_HEADER = 'X-Wearwise-Client';

/**
 * Gợi ý "máy này từng đăng nhập" để lúc mở lại trang biết có nên thử khôi phục phiên hay không.
 * KHÔNG phải thông tin bí mật — refresh token thật nằm trong cookie HttpOnly do server đặt.
 */
const SESSION_HINT_KEY = 'wearwise_username';

/**
 * Access token chỉ sống trong bộ nhớ của tab, cố tình KHÔNG đưa vào localStorage: token nằm
 * trong localStorage thì mọi đoạn script chạy được trên trang đều đọc được, và nó còn sống
 * sót qua cả lúc đóng trình duyệt. Ở đây, đóng tab là mất — phiên dài do cookie HttpOnly giữ,
 * và cookie đó JavaScript không chạm tới được.
 */
let accessToken = null;

export function getToken() {
  return accessToken;
}

export function getStoredUsername() {
  try {
    return localStorage.getItem(SESSION_HINT_KEY);
  } catch {
    return null;
  }
}

/** Có dấu vết phiên cũ không? Dùng để quyết định có gọi /refresh lúc khởi động hay không. */
export function hasSessionHint() {
  return Boolean(getStoredUsername());
}

export function storeSession(token, username) {
  accessToken = token;
  try {
    localStorage.setItem(SESSION_HINT_KEY, username);
  } catch {
    // localStorage bị chặn — phiên vẫn chạy bình thường, chỉ mất khả năng tự đăng nhập lại
    // sau khi tải lại trang.
  }
}

export function clearSession() {
  accessToken = null;
  try {
    localStorage.removeItem(SESSION_HINT_KEY);
  } catch {
    /* không có gì để dọn */
  }
}

export class ApiError extends Error {
  /**
   * @param code    mã lỗi ổn định từ server (vd 'CLOTHING_ITEM_IN_USE') — hãy rẽ nhánh theo mã
   *                này thay vì so khớp `message`, vì lời văn có thể đổi bất cứ lúc nào.
   * @param details thông tin phụ đi kèm mã lỗi, vd số outfit đang dùng món đồ không xóa được.
   */
  constructor(status, message, fieldErrors = {}, code = null, details = {}) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors;
    this.code = code;
    this.details = details;
  }
}

/**
 * Gia hạn access token bằng cookie refresh token. Nhiều request cùng gặp 401 sẽ dùng chung một
 * lần gọi /refresh thay vì mỗi request tự gọi (nếu không, refresh token xoay vòng sẽ tự vô hiệu
 * lẫn nhau).
 *
 * <p>Không kiểm tra trước xem có refresh token hay không được nữa — cookie HttpOnly vô hình với
 * JavaScript. Cứ gọi và để server trả lời; thất bại thì coi như chưa đăng nhập.
 */
let pendingRefresh = null;

export function refreshAccessToken() {
  if (!pendingRefresh) {
    pendingRefresh = fetch(apiUrl('/api/auth/refresh'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', [CLIENT_HEADER]: 'web' },
      credentials: 'include',
    })
      .then(async (response) => {
        if (!response.ok) {
          return null;
        }
        const data = await response.json();
        storeSession(data.accessToken, data.username);
        return data;
      })
      .catch(() => null)
      .finally(() => {
        pendingRefresh = null;
      });
  }

  return pendingRefresh;
}

async function handleResponse(response, auth) {
  // Chỉ request đang mang token mới coi 401 là "phiên hết hạn". Với /login, /register,
  // /refresh... thì 401 nghĩa là sai thông tin đăng nhập — phải giữ nguyên thông báo của
  // server, và tuyệt đối không xóa phiên hiện có chỉ vì gõ nhầm mật khẩu.
  if (response.status === 401 && auth) {
    clearSession();
    window.dispatchEvent(new Event('wearwise:unauthorized'));
    throw new ApiError(401, 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
  }

  if (response.status === 204) {
    return null;
  }

  let data = null;
  const text = await response.text();
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = null;
    }
  }

  if (!response.ok) {
    const message = data?.message || `Yêu cầu thất bại (HTTP ${response.status}).`;
    throw new ApiError(response.status, message, data?.errors || {}, data?.code || null, data?.details || {});
  }

  return data;
}

/**
 * Gửi lại request một lần sau khi gia hạn token, để access token hết hạn không làm gián đoạn
 * thao tác. Chỉ thử gia hạn khi máy này từng đăng nhập — tránh bắn một lượt /refresh vô ích
 * mỗi lần khách vãng lai chạm vào endpoint cần quyền.
 */
async function sendWithRetry(send, auth) {
  let response = await send(getToken());

  if (response.status === 401 && auth && hasSessionHint()) {
    const refreshed = await refreshAccessToken();
    if (refreshed) {
      response = await send(refreshed.accessToken);
    }
  }

  return handleResponse(response, auth);
}

export async function apiFetch(path, { method = 'GET', body, auth = true } = {}) {
  const send = (token) => {
    const headers = { 'Content-Type': 'application/json', [CLIENT_HEADER]: 'web' };
    if (auth && token) {
      headers.Authorization = `Bearer ${token}`;
    }

    return fetch(apiUrl(path), {
      method,
      headers,
      // Cần cho cookie refresh token khi frontend và backend nằm ở hai domain khác nhau.
      credentials: 'include',
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  };

  return sendWithRetry(send, auth);
}

/**
 * Tải một file do server sinh ra (CSV xuất tủ đồ, file mẫu) rồi lưu xuống máy.
 *
 * <p>Không dùng được `apiFetch`: nó parse mọi phản hồi thành JSON. Cũng không dùng được thẻ
 * `<a download>` trỏ thẳng tới endpoint, vì access token nằm trong bộ nhớ chứ không phải cookie —
 * trình duyệt sẽ gửi một request không có header Authorization và nhận 401. Vì vậy phải fetch kèm
 * token rồi tự tạo blob URL.</p>
 */
export async function apiDownload(path, fallbackFilename) {
  const send = (token) => {
    const headers = { [CLIENT_HEADER]: 'web' };
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }

    return fetch(apiUrl(path), { headers, credentials: 'include' });
  };

  let response = await send(getToken());
  if (response.status === 401 && hasSessionHint()) {
    const refreshed = await refreshAccessToken();
    if (refreshed) {
      response = await send(refreshed.accessToken);
    }
  }

  if (!response.ok) {
    // Lỗi thì server trả JSON như mọi endpoint khác, không phải file.
    const text = await response.text();
    let data = null;
    try {
      data = JSON.parse(text);
    } catch {
      data = null;
    }
    throw new ApiError(
      response.status,
      data?.message || `Không tải được file (HTTP ${response.status}).`,
      data?.errors || {},
      data?.code || null
    );
  }

  const blob = await response.blob();
  const disposition = response.headers.get('Content-Disposition') || '';
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition);
  const filename = match ? decodeURIComponent(match[1]) : fallbackFilename;

  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  // Thu hồi ngay là Firefox huỷ luôn lần tải chưa kịp bắt đầu; hoãn một nhịp cho chắc.
  setTimeout(() => URL.revokeObjectURL(url), 2000);

  return filename;
}

// Tải file (multipart) — trình duyệt tự đặt Content-Type kèm boundary, không set thủ công.
export async function apiUpload(path, formData, { method = 'POST' } = {}) {
  const send = (token) => {
    const headers = { [CLIENT_HEADER]: 'web' };
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }

    return fetch(apiUrl(path), { method, headers, credentials: 'include', body: formData });
  };

  return sendWithRetry(send, true);
}
