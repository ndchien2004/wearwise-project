import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import * as authApi from '../api/auth';
import { clearSession, getStoredUsername, getToken, storeSession } from '../api/client';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [username, setUsername] = useState(() => (getToken() ? getStoredUsername() : null));
  const [user, setUser] = useState(null);

  useEffect(() => {
    const onUnauthorized = () => {
      setUsername(null);
      setUser(null);
    };
    window.addEventListener('wearwise:unauthorized', onUnauthorized);
    return () => window.removeEventListener('wearwise:unauthorized', onUnauthorized);
  }, []);

  // Hồ sơ đầy đủ (có email) được tải riêng — token chỉ mang tên đăng nhập.
  useEffect(() => {
    if (!username) {
      setUser(null);
      return undefined;
    }

    let cancelled = false;
    authApi
      .getCurrentUser()
      .then((profile) => {
        if (!cancelled) setUser(profile);
      })
      .catch(() => {
        // Lỗi mạng hoặc token hết hạn — không chặn giao diện, apiFetch đã lo phần 401.
      });

    return () => {
      cancelled = true;
    };
  }, [username]);

  const applySession = useCallback((response) => {
    storeSession(response.accessToken, response.refreshToken, response.username);
    setUsername(response.username);
  }, []);

  const login = useCallback(
    async (name, password) => applySession(await authApi.login(name, password)),
    [applySession]
  );

  // Bước 1: gửi mã OTP (chưa tạo tài khoản). Bước 2: xác nhận OTP xong thì tạo tài khoản +
  // đăng nhập luôn bằng cặp token trả về.
  const registerRequestOtp = useCallback(
    (name, email, password) => authApi.registerRequestOtp(name, email, password),
    []
  );

  const registerVerify = useCallback(
    async (email, otp) => {
      const response = await authApi.registerVerify(email, otp);
      applySession(response);
      // Bật cờ để hiện vòng hướng dẫn lần đầu ngay sau khi tạo tài khoản thành công.
      try {
        localStorage.setItem('wearwise_onboarding_pending', '1');
      } catch {
        // localStorage bị chặn — bỏ qua, chỉ mất phần hướng dẫn.
      }
      return response;
    },
    [applySession]
  );

  // Đổi mật khẩu trả về cặp token mới nên thiết bị hiện tại không bị đăng xuất.
  const changePassword = useCallback(
    async (currentPassword, newPassword) =>
      applySession(await authApi.changePassword(currentPassword, newPassword)),
    [applySession]
  );

  // Cập nhật hồ sơ đang giữ trong context (vd sau khi đổi ảnh đại diện) mà không phải gọi lại /me.
  const updateUser = useCallback((profile) => setUser(profile), []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } catch {
      // Token có thể đã hết hạn — vẫn xóa phiên phía client.
    }
    clearSession();
    setUsername(null);
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({
      username,
      user,
      isAuthenticated: Boolean(username),
      login,
      registerRequestOtp,
      registerVerify,
      logout,
      changePassword,
      updateUser,
    }),
    [username, user, login, registerRequestOtp, registerVerify, logout, changePassword, updateUser]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth phải được dùng bên trong AuthProvider');
  }
  return context;
}
