import { apiFetch } from './client';

function buildQuery(params) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      query.set(key, value);
    }
  });
  const s = query.toString();
  return s ? `?${s}` : '';
}

export function findOutfits(filters = {}) {
  return apiFetch(`/api/outfits${buildQuery(filters)}`);
}

export function getOutfit(id) {
  return apiFetch(`/api/outfits/${id}`);
}

export function createOutfit(payload) {
  return apiFetch('/api/outfits', { method: 'POST', body: payload });
}

export function updateOutfit(id, payload) {
  return apiFetch(`/api/outfits/${id}`, { method: 'PUT', body: payload });
}

export function deleteOutfit(id) {
  return apiFetch(`/api/outfits/${id}`, { method: 'DELETE' });
}

export function setOutfitFavorite(id, favorite) {
  return apiFetch(`/api/outfits/${id}/favorite`, { method: 'PATCH', body: { favorite } });
}

export function markOutfitWorn(id) {
  return apiFetch(`/api/outfits/${id}/wear`, { method: 'PATCH' });
}

export function suggestOutfits(temperature, raining, limit = 6) {
  return apiFetch(`/api/outfits/suggestions?temperature=${temperature}&raining=${raining}&limit=${limit}`);
}
