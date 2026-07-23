import { apiFetch } from './client';

/** Trạng thái cấu hình Gemini (để ẩn/hiện phần gợi ý AI). */
export function getAiStatus() {
  return apiFetch('/api/ai/status');
}

/**
 * Nhờ AI phối 2-3 bộ đồ từ tủ, dựa trên thời tiết + tone màu muốn mặc.
 * payload: { temperature, raining, weatherDescription, tone? }
 */
export function suggestAiOutfits(payload) {
  return apiFetch('/api/ai/outfit-suggestions', { method: 'POST', body: payload });
}
