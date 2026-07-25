import { useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import ChangePasswordModal from './ChangePasswordModal';
import { Button, Toast } from './ui';

const NAV_ITEMS = [
  { to: '/', emoji: '🏠', label: 'Tổng quan', end: true },
  { to: '/wardrobe', emoji: '👕', label: 'Tủ đồ' },
  { to: '/outfits', emoji: '🧢', label: 'Outfit' },
  { to: '/calendar', emoji: '📅', label: 'Lịch phối đồ' },
  { to: '/suggestions', emoji: '🌦️', label: 'Gợi ý thời tiết' },
  { to: '/try-on', emoji: '🪞', label: 'Thử đồ ảo' },
  { to: '/analytics', emoji: '📊', label: 'Thống kê' },
  { to: '/share', emoji: '🔗', label: 'Chia sẻ' },
];

const COLLAPSE_KEY = 'wearwise_sidebar_collapsed';

export default function Layout() {
  const { username, user, logout } = useAuth();
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(() => localStorage.getItem(COLLAPSE_KEY) === '1');
  const [changingPassword, setChangingPassword] = useState(false);
  const [toast, setToast] = useState(null);

  // Tài khoản tạo trước khi có tính năng quên mật khẩu thì chưa có email — nhắc người dùng bổ sung.
  const needsEmail = Boolean(user) && !user.email;

  const toggleCollapsed = () => {
    setCollapsed((prev) => {
      const next = !prev;
      localStorage.setItem(COLLAPSE_KEY, next ? '1' : '0');
      return next;
    });
  };

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <div className={`app-shell ${collapsed ? 'is-collapsed' : ''}`}>
      <aside className={`sidebar ${collapsed ? 'is-collapsed' : ''}`}>
        <div className="sidebar-top">
          <div className="sidebar-logo">
            {collapsed ? 'W' : <>Wear<span>Wise</span></>}
          </div>
          <button
            type="button"
            className="sidebar-toggle"
            onClick={toggleCollapsed}
            title={collapsed ? 'Mở rộng' : 'Thu gọn'}
            aria-label={collapsed ? 'Mở rộng thanh bên' : 'Thu gọn thanh bên'}
          >
            {collapsed ? '»' : '«'}
          </button>
        </div>

        {NAV_ITEMS.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
            title={collapsed ? item.label : undefined}
          >
            <span className="nav-emoji">{item.emoji}</span>
            <span className="nav-label">{item.label}</span>
          </NavLink>
        ))}

        <div className="sidebar-footer">
          <NavLink
            to="/profile"
            className={({ isActive }) => `sidebar-user ${isActive ? 'active' : ''}`}
            title={needsEmail ? `${username} — chưa có email` : username}
          >
            <span className="nav-emoji">👤</span>
            <span className="nav-label">{username}</span>
            {needsEmail && <span className="sidebar-user-alert" title="Chưa có email">!</span>}
          </NavLink>
          <Button
            size="sm"
            onClick={() => setChangingPassword(true)}
            title={collapsed ? 'Đổi mật khẩu' : undefined}
          >
            <span className="nav-emoji">🔑</span>
            <span className="nav-label">Đổi mật khẩu</span>
          </Button>
          <Button variant="danger" size="sm" onClick={handleLogout}>
            <span className="nav-emoji">🚪</span>
            <span className="nav-label">Đăng xuất</span>
          </Button>
        </div>
      </aside>

      <main className="main-content">
        <Outlet />
      </main>

      {changingPassword && (
        <ChangePasswordModal onClose={() => setChangingPassword(false)} onSuccess={setToast} />
      )}
      <Toast message={toast} onDismiss={() => setToast(null)} />
    </div>
  );
}
