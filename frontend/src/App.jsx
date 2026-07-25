import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import Layout from './components/Layout';
import { useAuth } from './context/AuthContext';
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
        <Route path="/" element={<DashboardPage />} />
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
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
