import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import * as authApi from '../api/auth';
import { clearSession, getStoredUsername, getToken, storeSession } from '../api/client';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [username, setUsername] = useState(() => (getToken() ? getStoredUsername() : null));

  useEffect(() => {
    const onUnauthorized = () => setUsername(null);
    window.addEventListener('wearwise:unauthorized', onUnauthorized);
    return () => window.removeEventListener('wearwise:unauthorized', onUnauthorized);
  }, []);

  const login = useCallback(async (name, password) => {
    const response = await authApi.login(name, password);
    storeSession(response.accessToken, response.username);
    setUsername(response.username);
  }, []);

  const register = useCallback(async (name, password) => {
    const response = await authApi.register(name, password);
    storeSession(response.accessToken, response.username);
    setUsername(response.username);
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } catch {
      // Token có thể đã hết hạn — vẫn xóa phiên phía client.
    }
    clearSession();
    setUsername(null);
  }, []);

  const value = useMemo(
    () => ({ username, isAuthenticated: Boolean(username), login, register, logout }),
    [username, login, register, logout]
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
