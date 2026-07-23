import { apiUpload } from './client';

/** Tải ảnh minh họa món đồ lên Cloudinary, trả về { url }. */
export function uploadClothingImage(file) {
  const formData = new FormData();
  formData.append('file', file);
  return apiUpload('/api/images/clothing', formData);
}

/** Tải ảnh đại diện outfit lên Cloudinary, trả về { url }. */
export function uploadOutfitImage(file) {
  const formData = new FormData();
  formData.append('file', file);
  return apiUpload('/api/images/outfit', formData);
}
