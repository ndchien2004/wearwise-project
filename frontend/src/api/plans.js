import { apiFetch } from './client';

export function findPlans(start, end) {
  const query = new URLSearchParams();
  if (start) query.set('start', start);
  if (end) query.set('end', end);
  const s = query.toString();
  return apiFetch(`/api/outfit-plans${s ? `?${s}` : ''}`);
}

export function createPlan(payload) {
  return apiFetch('/api/outfit-plans', { method: 'POST', body: payload });
}

export function updatePlan(id, payload) {
  return apiFetch(`/api/outfit-plans/${id}`, { method: 'PUT', body: payload });
}

export function completePlan(id) {
  return apiFetch(`/api/outfit-plans/${id}/complete`, { method: 'PATCH' });
}

export function deletePlan(id) {
  return apiFetch(`/api/outfit-plans/${id}`, { method: 'DELETE' });
}
