import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import * as authApi from '../api/auth';
import { clearSession, hasSessionHint, refreshAccessToken, storeSession } from '../api/client';
import { Loading } from '../components/ui';

const AuthContext = createContext(null);

function SessionRestoreScreen() {
  return <Loading>Đang khôi phục phiên...</Loading>;
}

export function AuthProvider({ children }) {
  const [username, setUsername] = useState(null);
  const [user, setUser] = useState(null);
  /** Đã gọi xong /me chưa (thành công hay thất bại đều tính) — xem useEffect bên dưới. */
  const [userLoaded, setUserLoaded] = useState(false);
  // Access token nằm trong bộ nhớ nên tải lại trang là mất. Nếu máy này từng đăng nhập thì
  // phải đổi cookie refresh token lấy token mới TRƯỚC KHI dựng giao diện — bằng không
  // RequireAuth sẽ thấy "chưa đăng nhập" và đá người dùng về trang đăng nhập mỗi lần bấm F5.
  const [isRestoring, setIsRestoring] = useState(() => hasSessionHint());

  useEffect(() => {
    if (!isRestoring) return undefined;

    let cancelled = false;
    refreshAccessToken()
      .then((session) => {
        if (cancelled) return;
        if (session) {
          setUsername(session.username);
        } else {
          // Cookie đã hết hạn hoặc bị thu hồi — dọn luôn dấu vết để lần sau khỏi thử lại.
          clearSession();
        }
      })
      .finally(() => {
        if (!cancelled) setIsRestoring(false);
      });

    return () => {
      cancelled = true;
    };
  }, [isRestoring]);

  useEffect(() => {
    const onUnauthorized = () => {
      setUsername(null);
      setUser(null);
    };
    window.addEventListener('wearwise:unauthorized', onUnauthorized);
    return () => window.removeEventListener('wearwise:unauthorized', onUnauthorized);
  }, []);

  // Hồ sơ đầy đủ (có email, vai trò) được tải riêng — token chỉ mang tên đăng nhập.
  useEffect(() => {
    if (!username) {
      setUser(null);
      setUserLoaded(false);
      return undefined;
    }

    let cancelled = false;
    setUserLoaded(false);
    authApi
      .getCurrentUser()
      .then((profile) => {
        if (!cancelled) setUser(profile);
      })
      .catch(() => {
        // Lỗi mạng hoặc token hết hạn — không chặn giao diện, apiFetch đã lo phần 401.
      })
      .finally(() => {
        // Đánh dấu "đã hỏi xong" kể cả khi thất bại: nơi nào chờ vai trò người dùng (vd cổng
        // vào trang quản trị) cần biết lúc nào thì thôi chờ, nếu không sẽ kẹt màn hình tải
        // vĩnh viễn mỗi khi request này hỏng.
        if (!cancelled) setUserLoaded(true);
      });

    return () => {
      cancelled = true;
    };
  }, [username]);

  const applySession = useCallback((response) => {
    // response.refreshToken không còn tồn tại: nó đi bằng cookie HttpOnly do server đặt.
    storeSession(response.accessToken, response.username);
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
      userLoaded,
      isAdmin: user?.role === 'ADMIN',
      isAuthenticated: Boolean(username),
      login,
      registerRequestOtp,
      registerVerify,
      logout,
      changePassword,
      updateUser,
    }),
    [username, user, userLoaded, login, registerRequestOtp, registerVerify, logout, changePassword, updateUser]
  );

  // Chưa biết người dùng đã đăng nhập hay chưa thì chưa dựng router — hiện trạng thái chờ,
  // nếu không sẽ có một nhịp nháy sang trang đăng nhập rồi mới quay lại.
  if (isRestoring) {
    return <SessionRestoreScreen />;
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth phải được dùng bên trong AuthProvider');
  }
  return context;
}
