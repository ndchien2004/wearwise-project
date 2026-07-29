import { apiFetch } from './client';

function buildQuery(params) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      query.set(key, value);
    }
  });
  const s = query.toString();
  return s ? `?${s}` : '';
}

/**
 * Danh sách món đồ — **đã cắt trang ở server**. Trả về đối tượng Page của Spring Data:
 * `{ content, totalPages, totalElements, number, ... }`, không phải mảng.
 *
 * @param filters gồm cả bộ lọc (keyword, category...) lẫn phân trang (`page` 0-based, `size`)
 */
export function findItems(filters = {}) {
  return apiFetch(`/api/clothing-items${buildQuery(filters)}`);
}

/**
 * Lấy trọn tủ đồ trong một lần gọi, trả về mảng phẳng.
 *
 * Chỉ dùng cho ô chọn món khi phối outfit: ở đó người dùng phải chọn được bất kỳ món nào, cắt
 * trang sẽ khiến món ở trang sau không bao giờ chọn tới. Đừng dùng cho danh sách hiển thị —
 * đó chính là thứ vừa được sửa đi.
 */
export function findAllItems(filters = {}) {
  return findItems({ ...filters, unpaged: true }).then((page) => page.content ?? []);
}

export function getItem(id) {
  return apiFetch(`/api/clothing-items/${id}`);
}

export function createItem(payload) {
  return apiFetch('/api/clothing-items', { method: 'POST', body: payload });
}

export function updateItem(id, payload) {
  return apiFetch(`/api/clothing-items/${id}`, { method: 'PUT', body: payload });
}

export function deleteItem(id) {
  return apiFetch(`/api/clothing-items/${id}`, { method: 'DELETE' });
}

/** Ẩn món đồ (xóa mềm) — dùng khi xóa cứng bị chặn vì món còn được tham chiếu. */
export function archiveItem(id) {
  return apiFetch(`/api/clothing-items/${id}/archive`, { method: 'PATCH' });
}

export function restoreItem(id) {
  return apiFetch(`/api/clothing-items/${id}/restore`, { method: 'PATCH' });
}

export function getArchivedItems() {
  return apiFetch('/api/clothing-items/archived');
}

export function getOutfitsUsingItem(id) {
  return apiFetch(`/api/clothing-items/${id}/outfits`);
}

export function setItemFavorite(id, favorite) {
  return apiFetch(`/api/clothing-items/${id}/favorite`, { method: 'PATCH', body: { favorite } });
}

export function markItemWorn(id) {
  return apiFetch(`/api/clothing-items/${id}/wear`, { method: 'PATCH' });
}

export function getLeastWornItems(limit = 5) {
  return apiFetch(`/api/clothing-items/least-worn?limit=${limit}`);
}

export function getMostWornItems(limit = 5) {
  return apiFetch(`/api/clothing-items/most-worn?limit=${limit}`);
}

export function getRecentlyWornItems(limit = 5) {
  return apiFetch(`/api/clothing-items/recently-worn?limit=${limit}`);
}

/** Thêm nhiều món cùng lúc (một giao dịch: sai một món thì hủy cả lô). */
export function createItems(items) {
  return apiFetch('/api/clothing-items/batch', { method: 'POST', body: { items } });
}
