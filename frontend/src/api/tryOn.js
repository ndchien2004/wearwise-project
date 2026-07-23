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

export function generateTryOn(itemId) {
  return apiFetch(`/api/try-on/items/${itemId}`, { method: 'POST' });
}

export function listTryOns() {
  return apiFetch('/api/try-on');
}

// Lịch sử ảnh thử đồ của riêng một món đồ.
export function listTryOnsForItem(itemId) {
  return apiFetch(`/api/try-on/items/${itemId}`);
}

// Thử nguyên một outfit (ghép tất cả món có ảnh).
export function generateOutfitTryOn(outfitId) {
  return apiFetch(`/api/try-on/outfits/${outfitId}`, { method: 'POST' });
}

// Lịch sử ảnh thử đồ của riêng một outfit.
export function listTryOnsForOutfit(outfitId) {
  return apiFetch(`/api/try-on/outfits/${outfitId}`);
}

export function deleteTryOn(id) {
  return apiFetch(`/api/try-on/${id}`, { method: 'DELETE' });
}
