import { apiUpload } from './client';

/** Tải ảnh minh họa món đồ lên Cloudinary, trả về { url }. */
export function uploadClothingImage(file) {
  const formData = new FormData();
  formData.append('file', file);
  return apiUpload('/api/images/clothing', formData);
}
