import { apiFetch, apiUpload } from './client';

export function getTryOnProfile() {
  return apiFetch('/api/try-on/profile');
}

export function uploadBodyPhoto(file) {
  const formData = new FormData();
  formData.append('file', file);
  return apiUpload('/api/try-on/body-photo', formData);
}

export function deleteBodyPhoto() {
  return apiFetch('/api/try-on/body-photo', { method: 'DELETE' });
}

/**
 * Ghép một món đồ lên ảnh người dùng.
 *
 * @param baseResultId truyền id của một ảnh kết quả trước đó để **mặc chồng** món này lên trên nó
 *                     (vd đã mặc quần, giờ mặc thêm áo). Bỏ trống thì ghép từ ảnh cơ thể gốc.
 */
export function generateTryOn(itemId, baseResultId) {
  const query = baseResultId ? `?baseResultId=${baseResultId}` : '';
  return apiFetch(`/api/try-on/items/${itemId}${query}`, { method: 'POST' });
}

export function listTryOns() {
  return apiFetch('/api/try-on');
}

// Lịch sử ảnh thử đồ của riêng một món đồ.
export function listTryOnsForItem(itemId) {
  return apiFetch(`/api/try-on/items/${itemId}`);
}

// Thử nguyên một outfit (ghép tất cả món có ảnh trong một lần gọi).
export function generateOutfitTryOn(outfitId, baseResultId) {
  const query = baseResultId ? `?baseResultId=${baseResultId}` : '';
  return apiFetch(`/api/try-on/outfits/${outfitId}${query}`, { method: 'POST' });
}

// Lịch sử ảnh thử đồ của riêng một outfit.
export function listTryOnsForOutfit(outfitId) {
  return apiFetch(`/api/try-on/outfits/${outfitId}`);
}

export function deleteTryOn(id) {
  return apiFetch(`/api/try-on/${id}`, { method: 'DELETE' });
}
