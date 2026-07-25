import { Link, useLocation } from 'react-router-dom';

const TABS = [
  { to: '/wardrobe', emoji: '👕', label: 'Tủ đồ' },
  { to: '/outfits', emoji: '🧢', label: 'Outfit' },
];

/**
 * Tủ đồ và Outfit là hai mặt của cùng một mục nên dùng chung một tiêu đề: hai nút cạnh nhau
 * thay cho hai mục điều hướng riêng. Vẫn là hai route riêng để link chi tiết (/wardrobe/:id,
 * /outfits/:id) và nút "← quay lại" ở trang chi tiết không phải đổi.
 */
export default function ClosetSwitch() {
  const { pathname } = useLocation();

  return (
    <div className="page-switch" role="tablist" aria-label="Tủ đồ và Outfit">
      {TABS.map((tab) => {
        const active = pathname === tab.to || pathname.startsWith(`${tab.to}/`);
        return (
          <Link
            key={tab.to}
            to={tab.to}
            role="tab"
            aria-selected={active}
            className={`page-switch-btn ${active ? 'is-active' : ''}`}
          >
            <span className="page-switch-emoji">{tab.emoji}</span>
            {tab.label}
          </Link>
        );
      })}
    </div>
  );
}
