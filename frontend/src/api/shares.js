import { apiFetch } from './client';

export function createShare(targetType, targetId, expiresInDays = null) {
  return apiFetch('/api/shares', {
    method: 'POST',
    body: { targetType, targetId, expiresInDays },
  });
}

export function listMyShares() {
  return apiFetch('/api/shares');
}

export function getSharePreview(code) {
  return apiFetch(`/api/shares/${encodeURIComponent(code)}`);
}

export function importShare(code) {
  return apiFetch(`/api/shares/${encodeURIComponent(code)}/import`, { method: 'POST' });
}

export function revokeShare(code) {
  return apiFetch(`/api/shares/${encodeURIComponent(code)}`, { method: 'DELETE' });
}
