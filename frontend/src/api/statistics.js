import { apiFetch } from './client';

export function getStatistics() {
  return apiFetch('/api/statistics');
}

/** Lịch sử mặc trong một khoảng ngày. Bỏ trống from/to thì server lấy tháng hiện tại. */
export function getWearHistory({ from, to, limit } = {}) {
  const query = new URLSearchParams();
  if (from) query.set('from', from);
  if (to) query.set('to', to);
  if (limit) query.set('limit', String(limit));
  const search = query.toString();
  return apiFetch(`/api/statistics/history${search ? `?${search}` : ''}`);
}
