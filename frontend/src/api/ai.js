import { apiFetch, apiUpload } from './client';

/** Trạng thái cấu hình Gemini (để ẩn/hiện phần gợi ý AI). */
export function getAiStatus() {
  return apiFetch('/api/ai/status');
}

/**
 * Nhờ AI nhận diện món đồ trong ảnh để điền sẵn form.
 * Ảnh phải được thu nhỏ trước bằng shrinkImageForAi() — xem utils/image.js.
 */
export function analyzeClothingImage(file) {
  const formData = new FormData();
  formData.append('file', file);
  return apiUpload('/api/ai/clothing-items/analyze', formData);
}

/**
 * Nhờ AI phối 2-3 bộ đồ từ tủ, dựa trên thời tiết + tone màu muốn mặc.
 * payload: { temperature, raining, weatherDescription, tone? }
 */
export function suggestAiOutfits(payload) {
  return apiFetch('/api/ai/outfit-suggestions', { method: 'POST', body: payload });
}

/**
 * Nhờ AI xếp hạng các outfit CÓ SẴN theo mức phù hợp thời tiết.
 * payload: { temperature, raining, weatherDescription, tone? }
 */
export function rankAiOutfits(payload) {
  return apiFetch('/api/ai/outfit-ranking', { method: 'POST', body: payload });
}

/**
 * Nhờ AI lên kế hoạch mặc cho nhiều ngày dựa vào dự báo thời tiết.
 * payload: { days: [{ date, tempMin, tempMax, rainChance, description }], tone? }
 */
export function planAiWeek(payload) {
  return apiFetch('/api/ai/weekly-plan', { method: 'POST', body: payload });
}
