import { apiFetch, apiUpload } from './client';

let aiStatusPromise = null;

/**
 * Trạng thái cấu hình Gemini (để ẩn/hiện phần gợi ý AI). Server-side configuration cannot change
 * mid-session, so the answer is fetched once and shared — every mount of the item form used to
 * fire its own request.
 */
export function getAiStatus() {
  if (!aiStatusPromise) {
    aiStatusPromise = apiFetch('/api/ai/status').catch((error) => {
      aiStatusPromise = null;
      throw error;
    });
  }

  return aiStatusPromise;
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

// Kế hoạch mặc nhiều ngày đã chuyển sang api/wearPlans.js: luồng mới nhận thêm yêu cầu bằng lời
// của người dùng, có bước xem trước và lưu cả đợt trong một lần thay vì thêm từng ngày vào lịch.

/** Quét nhiều món trong một ảnh, trả về mảng gợi ý để người dùng soát lại. */
export function analyzeClothingImageBatch(file) {
  const formData = new FormData();
  formData.append('file', file);
  return apiUpload('/api/ai/clothing-items/analyze-batch', formData);
}
