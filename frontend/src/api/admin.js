import { apiFetch, apiUrl, CLIENT_HEADER, getToken, refreshAccessToken } from './client';

/**
 * API khu vực quản trị. Mọi endpoint đòi hỏi ROLE_ADMIN — người dùng thường gọi vào sẽ nhận 403
 * (chứ không phải 401), nghĩa là đăng nhập lại cũng không giải quyết được gì.
 */

function withQuery(path, params) {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    // Bỏ qua giá trị rỗng để không gửi ?query=&action= vô nghĩa lên server.
    if (value !== null && value !== undefined && value !== '') {
      search.set(key, value);
    }
  });

  const queryString = search.toString();
  return queryString ? `${path}?${queryString}` : path;
}

export function getOverview() {
  return apiFetch('/api/admin/overview');
}

export function listUsers({ query = '', page = 0, size = 20 } = {}) {
  return apiFetch(withQuery('/api/admin/users', { query, page, size }));
}

export function lockUser(username, { reason, durationMinutes }) {
  return apiFetch(`/api/admin/users/${encodeURIComponent(username)}/lock`, {
    method: 'POST',
    body: { reason, durationMinutes },
  });
}

export function unlockUser(username, { reason }) {
  return apiFetch(`/api/admin/users/${encodeURIComponent(username)}/unlock`, {
    method: 'POST',
    body: { reason },
  });
}

/** Gửi null ở trường nào thì trường đó quay về hạn mức mặc định của hệ thống. */
export function setRateLimit(username, { aiPerHour, externalPerHour }) {
  return apiFetch(`/api/admin/users/${encodeURIComponent(username)}/rate-limit`, {
    method: 'PUT',
    body: { aiPerHour, externalPerHour },
  });
}

export function listAuditEvents({ action = '', username = '', page = 0, size = 50 } = {}) {
  return apiFetch(withQuery('/api/admin/audit-events', { action, username, page, size }));
}

/**
 * Mở kênh nhận sự kiện kiểm toán theo thời gian thực.
 *
 * <p>Không dùng `EventSource` dù nó sinh ra đúng cho việc này: `EventSource` không cho đặt header,
 * mà access token của ứng dụng nằm trong bộ nhớ chứ không phải cookie. Cách duy nhất để dùng nó là
 * nhét token vào query string — token sẽ hiện trong log truy cập của mọi proxy trên đường đi. Đọc
 * stream bằng `fetch` giữ được header `Authorization` và đổi lại chỉ tốn khoảng hai chục dòng tự
 * tách khung SSE.
 *
 * @param onEvent  gọi với mỗi sự kiện kiểm toán mới
 * @param onOpen   gọi mỗi khi kết nối (hoặc kết nối lại) thành công
 * @param signal   AbortSignal để đóng kênh khi rời trang
 */
export async function streamAuditEvents({ onEvent, onOpen, signal }) {
  const connect = async (token) =>
    fetch(apiUrl('/api/admin/audit-events/stream'), {
      headers: {
        Authorization: `Bearer ${token}`,
        [CLIENT_HEADER]: 'web',
        Accept: 'text/event-stream',
      },
      credentials: 'include',
      signal,
    });

  let response = await connect(getToken());

  // Access token sống 15 phút còn kênh này mở lâu hơn thế, nên lần nối lại rất dễ gặp token hết
  // hạn. Gia hạn rồi thử lại đúng một lần.
  if (response.status === 401) {
    const refreshed = await refreshAccessToken();
    if (!refreshed) {
      throw new Error('Phiên đăng nhập đã hết hạn.');
    }
    response = await connect(refreshed.accessToken);
  }

  if (!response.ok || !response.body) {
    throw new Error(`Không mở được kênh nhật ký (HTTP ${response.status}).`);
  }

  onOpen?.();

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    const { value, done } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });

    // Khung SSE ngăn nhau bằng một dòng trống. Phần dư cuối buffer là khung chưa nhận đủ —
    // giữ lại chờ mảnh tiếp theo.
    const frames = buffer.split('\n\n');
    buffer = frames.pop() ?? '';

    for (const frame of frames) {
      const eventName = /^event:\s*(.*)$/m.exec(frame)?.[1]?.trim();
      if (eventName !== 'audit') continue; // bỏ qua 'connected' và 'heartbeat'

      const data = frame
        .split('\n')
        .filter((line) => line.startsWith('data:'))
        .map((line) => line.slice(5).trim())
        .join('');

      if (!data) continue;
      try {
        onEvent(JSON.parse(data));
      } catch {
        // Khung hỏng thì bỏ qua — nguồn dữ liệu tin cậy vẫn là danh sách tải qua REST.
      }
    }
  }
}
