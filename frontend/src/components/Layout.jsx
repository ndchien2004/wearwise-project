import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Button } from './ui';

const NAV_ITEMS = [
  { to: '/', emoji: '🏠', label: 'Tổng quan', end: true },
  { to: '/wardrobe', emoji: '👕', label: 'Tủ đồ' },
  { to: '/outfits', emoji: '🧢', label: 'Outfit' },
  { to: '/calendar', emoji: '📅', label: 'Lịch phối đồ' },
  { to: '/suggestions', emoji: '🌦️', label: 'Gợi ý thời tiết' },
  { to: '/try-on', emoji: '🪞', label: 'Thử đồ ảo' },
  { to: '/analytics', emoji: '📊', label: 'Thống kê' },
];

export default function Layout() {
  const { username, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="sidebar-logo">
          Wear<span>Wise</span>
        </div>

        {NAV_ITEMS.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
          >
            <span className="nav-emoji">{item.emoji}</span>
            {item.label}
          </NavLink>
        ))}

        <div className="sidebar-footer">
          <div className="sidebar-user" title={username}>
            👤 {username}
          </div>
          <Button variant="danger" size="sm" onClick={handleLogout}>
            Đăng xuất
          </Button>
        </div>
      </aside>

      <main className="main-content">
        <Outlet />
      </main>
    </div>
  );
}
