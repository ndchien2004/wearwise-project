import { apiFetch } from './client';

/**
 * Nhờ AI sinh kế hoạch từ một câu yêu cầu tự do. **Không lưu gì** — kết quả chỉ để xem trước.
 *
 * Endpoint nằm dưới /api/ai nên tính vào hạn mức AI (40 lượt/giờ): mỗi lần bấm là một lời gọi
 * Gemini tốn tiền thật, đừng gọi lại chỉ vì component render lại.
 *
 * payload: { request, days?, startDate?, tone?, forecast?: [{ date, tempMin, tempMax, rainChance, description }] }
 */
export function generateWearPlan(payload) {
  return apiFetch('/api/ai/wear-plans', { method: 'POST', body: payload });
}

/**
 * Lưu kế hoạch vừa xem trước.
 * payload: { title, userRequest, summary, days: [{ date, outfitId, note, replaceExisting }] }
 */
export function saveWearPlan(payload) {
  return apiFetch('/api/wear-plans', { method: 'POST', body: payload });
}

/** Các đợt đang phủ ngày hôm nay — thẻ kế hoạch ở trang chủ. */
export function findActiveWearPlans() {
  return apiFetch('/api/wear-plans?activeOnly=true');
}

