import { apiFetch } from './client';

export function register(username, password) {
  return apiFetch('/api/auth/register', { method: 'POST', body: { username, password }, auth: false });
}

export function login(username, password) {
  return apiFetch('/api/auth/login', { method: 'POST', body: { username, password }, auth: false });
}

export function getCurrentUser() {
  return apiFetch('/api/auth/me');
}

export function logout() {
  return apiFetch('/api/auth/logout', { method: 'POST' });
}
