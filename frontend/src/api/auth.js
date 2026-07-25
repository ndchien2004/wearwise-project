import { apiFetch, getRefreshToken } from './client';

export function register(username, email, password) {
  return apiFetch('/api/auth/register', {
    method: 'POST',
    body: { username, email, password },
    auth: false,
  });
}

export function login(username, password) {
  return apiFetch('/api/auth/login', { method: 'POST', body: { username, password }, auth: false });
}

export function getCurrentUser() {
  return apiFetch('/api/auth/me');
}

export function logout() {
  return apiFetch('/api/auth/logout', {
    method: 'POST',
    body: { refreshToken: getRefreshToken() },
  });
}

export function forgotPassword(email) {
  return apiFetch('/api/auth/forgot-password', { method: 'POST', body: { email }, auth: false });
}

export function validateResetToken(token) {
  return apiFetch(`/api/auth/reset-password/validate?token=${encodeURIComponent(token)}`, {
    auth: false,
  });
}

export function resetPassword(token, newPassword) {
  return apiFetch('/api/auth/reset-password', {
    method: 'POST',
    body: { token, newPassword },
    auth: false,
  });
}

export function changePassword(currentPassword, newPassword) {
  return apiFetch('/api/auth/change-password', {
    method: 'POST',
    body: { currentPassword, newPassword },
  });
}
