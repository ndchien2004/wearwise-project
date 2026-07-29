import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import Layout from './components/Layout';
import { Loading } from './components/ui';
import { useAuth } from './context/AuthContext';
import AdminPage from './pages/AdminPage';
import AuthPage from './pages/AuthPage';
import CalendarPage from './pages/CalendarPage';
import DashboardPage from './pages/DashboardPage';
import ItemDetailPage from './pages/ItemDetailPage';
import OutfitDetailPage from './pages/OutfitDetailPage';
import OutfitsPage from './pages/OutfitsPage';
import ProfilePage from './pages/ProfilePage';
import ResetPasswordPage from './pages/ResetPasswordPage';
import SharePage from './pages/SharePage';
import SuggestionsPage from './pages/SuggestionsPage';
import TryOnPage from './pages/TryOnPage';
import WardrobePage from './pages/WardrobePage';

function RequireAuth({ children }) {
  const { isAuthenticated } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    // Nhớ đường dẫn đang mở để sau khi đăng nhập quay lại đúng chỗ — quan trọng với
    // link chia sẻ: người nhận thường chưa đăng nhập khi bấm vào link.
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }

  return children;
}

/**
 * Ẩn khu vực quản trị khỏi người dùng thường. Đây chỉ là lớp giao diện — quyền thật do
 * `SecurityConfig` quyết định và server vẫn trả 403 dù ai đó gõ thẳng URL. Không được coi
 * kiểm tra ở đây là biện pháp bảo mật.
 *
 * `user` được tải bất đồng bộ sau khi có phiên, nên phải chờ nó về rồi mới quyết định — bằng
 * không admin bấm F5 ở /admin sẽ bị đẩy về trang chủ.
 */
function RequireAdmin({ children }) {
  const { userLoaded, isAdmin } = useAuth();

  // Chờ đến khi biết chắc vai trò. Dùng `userLoaded` chứ không phải `user != null`: nếu request
  // /me hỏng thì `user` mãi mãi null và màn hình chờ sẽ không bao giờ kết thúc.
  if (!userLoaded) {
    return <Loading>Đang kiểm tra quyền truy cập...</Loading>;
  }

  return isAdmin ? children : <Navigate to="/" replace />;
}

function AdminRoute() {
  return (
    <RequireAdmin>
      <AdminPage />
    </RequireAdmin>
  );
}

/**
 * Trang chủ khác nhau tùy vai trò: quản trị viên không có tủ đồ nên bảng điều khiển của người
 * dùng thường là một trang trống rỗng vô nghĩa với họ. Đẩy thẳng sang khu vực vận hành.
 */
function HomeRoute() {
  const { userLoaded, isAdmin } = useAuth();

  if (!userLoaded) {
    return <Loading />;
  }

  return isAdmin ? <Navigate to="/admin" replace /> : <DashboardPage />;
}

function LoginRoute() {
  const { isAuthenticated } = useAuth();
  const location = useLocation();

  if (isAuthenticated) {
    return <Navigate to={location.state?.from || '/'} replace />;
  }

  return <AuthPage />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginRoute />} />
      {/* Công khai: người dùng mở link trong email khi chưa đăng nhập được. */}
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route path="/" element={<HomeRoute />} />
        <Route path="/wardrobe" element={<WardrobePage />} />
        <Route path="/wardrobe/:id" element={<ItemDetailPage />} />
        <Route path="/outfits" element={<OutfitsPage />} />
        <Route path="/outfits/:id" element={<OutfitDetailPage />} />
        <Route path="/calendar" element={<CalendarPage />} />
        <Route path="/suggestions" element={<SuggestionsPage />} />
        <Route path="/try-on" element={<TryOnPage />} />
        {/* Thống kê đã gộp vào trang tài khoản — giữ redirect cho link/bookmark cũ. */}
        <Route path="/analytics" element={<Navigate to="/profile" replace />} />
        <Route path="/share" element={<SharePage />} />
        <Route path="/share/:code" element={<SharePage />} />
        <Route path="/profile" element={<ProfilePage />} />
        {/* Tab của khu vực quản trị là route riêng chứ không phải state trong component:
            menu trỏ thẳng vào từng tab, và link tới một tab cụ thể chia sẻ được.
            Khai báo hai dòng thay vì dùng segment tùy chọn `:tab?` cho khỏi phụ thuộc
            phiên bản react-router. */}
        <Route path="/admin" element={<AdminRoute />} />
        <Route path="/admin/:tab" element={<AdminRoute />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
