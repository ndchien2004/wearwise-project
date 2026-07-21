import { apiFetch } from './client';

export function getStatistics() {
  return apiFetch('/api/statistics');
}
