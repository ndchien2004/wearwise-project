import { apiFetch, apiUpload } from './client';

// Đăng ký 2 bước: xin mã OTP gửi qua email, rồi xác nhận mã để tạo tài khoản.
export function registerRequestOtp(username, email, password) {
  return apiFetch('/api/auth/register/request-otp', {
    method: 'POST',
    body: { username, email, password },
    auth: false,
  });
}

export function registerVerify(email, otp) {
  return apiFetch('/api/auth/register/verify', {
    method: 'POST',
    body: { email, otp },
    auth: false,
  });
}

export function login(username, password) {
  return apiFetch('/api/auth/login', { method: 'POST', body: { username, password }, auth: false });
}

export function getCurrentUser() {
  return apiFetch('/api/auth/me');
}

// Không gửi refresh token trong body: server tự đọc từ cookie HttpOnly rồi thu hồi và xóa nó.
export function logout() {
  return apiFetch('/api/auth/logout', { method: 'POST' });
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

// Tải ảnh đại diện lên Cloudinary; server trả về hồ sơ đã cập nhật (có avatarUrl mới).
// Nhận File hoặc Blob (ảnh đã cắt) — Blob không có .name nên đặt tên tệp mặc định.
export function uploadAvatar(file) {
  const form = new FormData();
  form.append('file', file, file.name || 'avatar.jpg');
  return apiUpload('/api/auth/avatar', form);
}

export function removeAvatar() {
  return apiFetch('/api/auth/avatar', { method: 'DELETE' });
}
