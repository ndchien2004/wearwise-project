/**
 * Gốc của API. Để trống khi chạy dev (Vite proxy /api sang localhost:8080) và khi frontend
 * được phục vụ cùng origin với backend. Khi deploy tách rời — ví dụ frontend trên Cloudflare
 * Pages, backend ở nơi khác — đặt VITE_API_BASE_URL lúc build, vd:
 * VITE_API_BASE_URL=https://api.wearwise.example.com
 */
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/+$/, '');

function apiUrl(path) {
  return `${API_BASE_URL}${path}`;
}

const TOKEN_KEY = 'wearwise_token';
const REFRESH_TOKEN_KEY = 'wearwise_refresh_token';
const USERNAME_KEY = 'wearwise_username';

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function getRefreshToken() {
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

export function getStoredUsername() {
  return localStorage.getItem(USERNAME_KEY);
}

export function storeSession(token, refreshToken, username) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USERNAME_KEY, username);

  if (refreshToken) {
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  }
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(USERNAME_KEY);
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
 * Gia hạn access token. Nhiều request cùng gặp 401 sẽ dùng chung một lần gọi /refresh
 * thay vì mỗi request tự gọi (nếu không, refresh token xoay vòng sẽ tự vô hiệu lẫn nhau).
 */
let pendingRefresh = null;

function refreshAccessToken() {
  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    return Promise.resolve(null);
  }

  if (!pendingRefresh) {
    pendingRefresh = fetch(apiUrl('/api/auth/refresh'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
      .then(async (response) => {
        if (!response.ok) {
          return null;
        }
        const data = await response.json();
        storeSession(data.accessToken, data.refreshToken, data.username);
        return data.accessToken;
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

/** Gửi lại request một lần sau khi gia hạn token, để access token hết hạn không làm gián đoạn thao tác. */
async function sendWithRetry(send, auth) {
  let response = await send(getToken());

  if (response.status === 401 && auth && getRefreshToken()) {
    const newToken = await refreshAccessToken();
    if (newToken) {
      response = await send(newToken);
    }
  }

  return handleResponse(response, auth);
}

export async function apiFetch(path, { method = 'GET', body, auth = true } = {}) {
  const send = (token) => {
    const headers = { 'Content-Type': 'application/json' };
    if (auth && token) {
      headers.Authorization = `Bearer ${token}`;
    }

    return fetch(apiUrl(path), {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  };

  return sendWithRetry(send, auth);
}

// Tải file (multipart) — trình duyệt tự đặt Content-Type kèm boundary, không set thủ công.
export async function apiUpload(path, formData, { method = 'POST' } = {}) {
  const send = (token) => {
    const headers = {};
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }

    return fetch(apiUrl(path), { method, headers, body: formData });
  };

  return sendWithRetry(send, true);
}
