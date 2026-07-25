import { useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import GameMenu from './GameMenu';

/**
 * `match` liệt kê các nhánh đường dẫn cùng thuộc một mục — dùng cho những mục đã gộp nhiều
 * trang vào chung (Tủ đồ + Outfit, Gợi ý + Lịch). Mục không có `to` là hành động tại chỗ.
 */
const MENU_ITEMS = [
  { key: 'home', to: '/', emoji: '🏠', label: 'Tổng quan', exact: true },
  { key: 'closet', to: '/wardrobe', emoji: '👕', label: 'Tủ đồ & Outfit', match: ['/wardrobe', '/outfits'] },
  { key: 'weather', to: '/suggestions', emoji: '🌦️', label: 'Gợi ý & Lịch', match: ['/suggestions', '/calendar'] },
  { key: 'tryon', to: '/try-on', emoji: '🪞', label: 'Thử đồ ảo' },
  { key: 'share', to: '/share', emoji: '🔗', label: 'Chia sẻ' },
  // Đổi mật khẩu không có mục riêng ở đây — nó đã nằm sẵn trong trang tài khoản.
  { key: 'profile', to: '/profile', emoji: '👤', label: 'Tài khoản & Thống kê' },
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
  const { username, user, logout } = useAuth();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);

  // Tài khoản tạo trước khi có tính năng quên mật khẩu thì chưa có email — nhắc người dùng bổ sung.
  const needsEmail = Boolean(user) && !user.email;

  const items = MENU_ITEMS.map((item) => ({
    ...item,
    current: matchesItem(pathname, item),
    alert: item.key === 'profile' && needsEmail,
  }));

  const currentLabel = items.find((item) => item.current)?.label;

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  const handlePick = (item) => {
    setMenuOpen(false);
    if (item.to) {
      navigate(item.to);
    } else if (item.action === 'logout') {
      handleLogout();
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

        <Link to="/" className="hud-logo">
          Wear<span>Wise</span>
        </Link>

        {currentLabel && <span className="hud-crumb">{currentLabel}</span>}

        <NavLink
          to="/profile"
          className={({ isActive }) => `hud-user ${isActive ? 'active' : ''}`}
          title={needsEmail ? `${username} — chưa có email` : `${username} — tài khoản & thống kê`}
        >
          <span aria-hidden="true">👤</span>
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
    </div>
  );
}
