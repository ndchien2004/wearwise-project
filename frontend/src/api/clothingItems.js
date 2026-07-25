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

export function findItems(filters = {}) {
  return apiFetch(`/api/clothing-items${buildQuery(filters)}`);
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
