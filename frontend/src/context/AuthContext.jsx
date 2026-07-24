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

  const register = useCallback(
    async (name, email, password) => applySession(await authApi.register(name, email, password)),
    [applySession]
  );

  // Đổi mật khẩu trả về cặp token mới nên thiết bị hiện tại không bị đăng xuất.
  const changePassword = useCallback(
    async (currentPassword, newPassword) =>
      applySession(await authApi.changePassword(currentPassword, newPassword)),
    [applySession]
  );

  const updateEmail = useCallback(async (currentPassword, email) => {
    setUser(await authApi.updateEmail(currentPassword, email));
  }, []);

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
      register,
      logout,
      changePassword,
      updateEmail,
    }),
    [username, user, login, register, logout, changePassword, updateEmail]
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
