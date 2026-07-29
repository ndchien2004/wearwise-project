import { useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useConfirm } from '../context/ConfirmContext';
import GameMenu from './GameMenu';
import OnboardingTour from './OnboardingTour';

const ONBOARDING_FLAG = 'wearwise_onboarding_pending';

/**
 * `match` liệt kê các nhánh đường dẫn cùng thuộc một mục — dùng cho những mục đã gộp nhiều
 * trang vào chung (Tủ đồ + Outfit, Gợi ý + Lịch). Mục không có `to` là hành động tại chỗ.
 */
const MENU_ITEMS = [
  { key: 'home', to: '/', emoji: '🏠', label: 'Trang chủ', exact: true },
  { key: 'closet', to: '/wardrobe', emoji: '👕', label: 'Tủ đồ & Outfit', match: ['/wardrobe', '/outfits'] },
  { key: 'weather', to: '/suggestions', emoji: '🌦️', label: 'Gợi ý & Lịch', match: ['/suggestions', '/calendar'] },
  { key: 'tryon', to: '/try-on', emoji: '🪞', label: 'Thử đồ ảo' },
  { key: 'share', to: '/share', emoji: '🔗', label: 'Chia sẻ' },
  // Đổi mật khẩu không có mục riêng ở đây — nó đã nằm sẵn trong trang tài khoản.
  { key: 'profile', to: '/profile', emoji: '👤', label: 'Tài khoản & Thống kê' },
  { key: 'logout', action: 'logout', emoji: '🚪', label: 'Đăng xuất', danger: true },
];

/**
 * Quản trị viên nhìn thấy một menu <b>hoàn toàn khác</b>, không phải menu thường cộng thêm một mục.
 *
 * <p>Đây là tài khoản vận hành, không phải tài khoản dùng ứng dụng: nó không có tủ đồ để quản lý,
 * không phối outfit, không thử đồ. Bày những mục đó ra chỉ tạo cảm giác admin cũng là một người
 * dùng bình thường — trong khi cả thiết kế phía server đi theo hướng ngược lại (admin quản lý tài
 * khoản, không chạm vào nội dung).
 *
 * <p>Vẫn giữ "Đăng xuất" vì không có nó thì không thoát ra được. Đổi mật khẩu vào qua ô tài khoản
 * ở góc phải thanh trên cùng.
 */
const ADMIN_MENU_ITEMS = [
  { key: 'admin-overview', to: '/admin', emoji: '📊', label: 'Tổng quan', exact: true },
  { key: 'admin-users', to: '/admin/users', emoji: '👥', label: 'Tài khoản' },
  { key: 'admin-audit', to: '/admin/audit', emoji: '📜', label: 'Nhật ký' },
  { key: 'logout', action: 'logout', emoji: '🚪', label: 'Đăng xuất', danger: true },
];

function isPathInside(pathname, base) {
  return pathname === base || pathname.startsWith(`${base}/`);
}

function matchesItem(pathname, item) {
  if (!item.to) return false;
  if (item.exact) return pathname === item.to;
  return (item.match || [item.to]).some((base) => isPathInside(pathname, base));
}

export default function Layout() {
  const { username, user, isAdmin, logout } = useAuth();
  const confirm = useConfirm();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);
  // Vòng hướng dẫn hiện một lần sau khi tạo tài khoản (cờ do bước đăng ký đặt).
  const [showTour, setShowTour] = useState(() => {
    try {
      return localStorage.getItem(ONBOARDING_FLAG) === '1';
    } catch {
      return false;
    }
  });

  const closeTour = () => {
    try {
      localStorage.removeItem(ONBOARDING_FLAG);
    } catch {
      // bỏ qua
    }
    setShowTour(false);
  };

  // Tài khoản tạo trước khi có tính năng quên mật khẩu thì chưa có email — nhắc người dùng bổ sung.
  const needsEmail = Boolean(user) && !user.email;

  const items = (isAdmin ? ADMIN_MENU_ITEMS : MENU_ITEMS).map((item) => ({
    ...item,
    current: matchesItem(pathname, item),
    alert: item.key === 'profile' && needsEmail,
  }));

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  const handlePick = async (item) => {
    setMenuOpen(false);
    if (item.to) {
      navigate(item.to);
    } else if (item.action === 'logout') {
      const ok = await confirm({
        title: 'Đăng xuất',
        message: 'Bạn có chắc chắn muốn đăng xuất khỏi tài khoản này không?',
        confirmLabel: 'Đăng xuất',
        cancelLabel: 'Ở lại',
        danger: true,
      });
      if (ok) handleLogout();
    }
  };

  return (
    <div className="app-shell">
      <header className="hud-bar">
        <button
          type="button"
          className="hud-menu-btn"
          onClick={() => setMenuOpen(true)}
          aria-haspopup="dialog"
          aria-expanded={menuOpen}
        >
          <span className="hud-menu-bars" aria-hidden="true">
            <i />
            <i />
            <i />
          </span>
          Menu
        </button>

        {/* Trang chủ của quản trị viên là khu vực vận hành, không phải bảng điều khiển tủ đồ. */}
        <Link to={isAdmin ? '/admin' : '/'} className="hud-logo">
          Wear<span>Wise</span>
        </Link>

        <NavLink
          to="/profile"
          className={({ isActive }) => `hud-user ${isActive ? 'active' : ''}`}
          title={needsEmail ? `${username} — chưa có email` : `${username} — tài khoản & thống kê`}
        >
          <span className="hud-user-avatar" aria-hidden="true">
            {user?.avatarUrl ? (
              <img src={user.avatarUrl} alt="" />
            ) : (
              (username || '?').charAt(0).toUpperCase()
            )}
          </span>
          <span className="hud-user-name">{username}</span>
          {needsEmail && <span className="hud-user-alert" title="Chưa có email">!</span>}
        </NavLink>
      </header>

      <main className="main-content">
        <Outlet />
      </main>

      {menuOpen && (
        <GameMenu items={items} onPick={handlePick} onClose={() => setMenuOpen(false)} />
      )}

      {showTour && <OnboardingTour onClose={closeTour} />}
    </div>
  );
}
