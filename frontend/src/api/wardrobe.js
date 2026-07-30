import { apiDownload, apiUpload } from './client';

/** Xuất toàn bộ tủ đồ (món chưa bị ẩn) ra CSV và lưu xuống máy. */
export function exportWardrobe() {
  return apiDownload('/api/wardrobe/export', 'wearwise-tu-do.csv');
}

/** Tải file mẫu: header + hai dòng ví dụ + ghi chú giá trị hợp lệ của từng cột. */
export function downloadTemplate() {
  return apiDownload('/api/wardrobe/template', 'wearwise-mau-tu-do.csv');
}

/**
 * Nhập tủ đồ từ CSV.
 *
 * Trả về { totalRows, created, skipped, failed, problems: [{ line, name, kind, message }] } —
 * chỉ những dòng có vấn đề nằm trong `problems`, dòng thêm thành công thì chỉ có con số.
 */
export function importWardrobe(file, { skipDuplicateNames = true } = {}) {
  const form = new FormData();
  form.append('file', file);
  form.append('skipDuplicateNames', String(skipDuplicateNames));
  return apiUpload('/api/wardrobe/import', form);
}
