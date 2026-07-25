import { useEffect, useState } from 'react';
import { getStatistics } from '../api/statistics';
import ChangePasswordModal from '../components/ChangePasswordModal';
import { Badge, Button, Loading, Toast } from '../components/ui';
import { useAuth } from '../context/AuthContext';
import { formatDateTime } from '../utils/date';

export default function ProfilePage() {
  const { user, username } = useAuth();

  const [changingPassword, setChangingPassword] = useState(false);
  const [toast, setToast] = useState(null);
  const [stats, setStats] = useState(null);

  const hasEmail = Boolean(user?.email);

  // Thống kê chỉ để trang trí — lỗi thì ẩn khối, không chặn phần tài khoản.
  useEffect(() => {
    getStatistics()
      .then(setStats)
      .catch(() => setStats(null));
  }, []);

  const tiles = stats
    ? [
        { label: 'Món đồ', value: stats.totalClothingItems, bg: 'var(--yellow)', emoji: '👕' },
        { label: 'Outfit', value: stats.totalOutfits, bg: 'var(--pink)', emoji: '🧢' },
        { label: 'Lượt mặc', value: stats.totalWearCount, bg: 'var(--green)', emoji: '👣' },
        { label: 'Yêu thích', value: stats.favoriteClothingItems + stats.favoriteOutfits, bg: 'var(--blue)', emoji: '⭐' },
      ]
    : [];

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">👤 Tài khoản</h1>
      </div>

      <div className="profile-hero nb-card">
        <div className="profile-avatar" aria-hidden="true">
          {(username || '?').charAt(0).toUpperCase()}
        </div>
        <div className="profile-hero-info">
          <h2 className="profile-hero-name">{username}</h2>
          <div className="badge-row">
            {hasEmail ? (
              <Badge color="green">✉️ {user.email}</Badge>
            ) : (
              <Badge color="red">✉️ Chưa có email</Badge>
            )}
            {user?.createdAt && <Badge color="purple">🎂 Tham gia {formatDateTime(user.createdAt)}</Badge>}
          </div>
        </div>
      </div>

      {stats && (
        <div className="stat-row">
          {tiles.map((tile) => (
            <div key={tile.label} className="stat-tile" style={{ background: tile.bg }}>
              <div className="stat-value">
                {tile.emoji} {tile.value}
              </div>
              <div className="stat-label">{tile.label}</div>
            </div>
          ))}
        </div>
      )}

      <div className="two-col">
        <div className="nb-card">
          <h3 className="chart-title">✉️ Email khôi phục</h3>

          {!user ? (
            <Loading>Đang tải hồ sơ...</Loading>
          ) : (
            <>
              <div className="profile-locked-field">
                <span className="profile-locked-value">
                  {user.email || <span style={{ color: 'var(--muted)' }}>chưa có</span>}
                </span>
                <span className="profile-locked-badge" title="Không thể thay đổi">🔒 Cố định</span>
              </div>

              {hasEmail ? (
                <p style={{ fontWeight: 600, color: 'var(--muted)', fontSize: 13.5, marginTop: 12 }}>
                  Email được đặt một lần duy nhất lúc tạo tài khoản và không thể thay đổi. Đây là địa
                  chỉ nhận link đặt lại mật khẩu khi bạn quên.
                </p>
              ) : (
                <div className="notice-banner" style={{ background: 'var(--yellow)', marginTop: 12 }}>
                  ⚠️ Tài khoản này được tạo trước khi email trở thành bắt buộc nên không có email, và
                  email không thể bổ sung sau. Bạn sẽ không dùng được chức năng quên mật khẩu — hãy
                  liên hệ quản trị viên nếu cần khôi phục.
                </div>
              )}
            </>
          )}
        </div>

        <div className="nb-card">
          <h3 className="chart-title">🔒 Bảo mật</h3>

          <dl className="account-facts">
            <dt>Tên đăng nhập</dt>
            <dd>{username}</dd>
            <dt>Email khôi phục</dt>
            <dd>{user?.email || <span style={{ color: 'var(--muted)' }}>chưa có</span>}</dd>
            {user?.createdAt && (
              <>
                <dt>Ngày tạo</dt>
                <dd>{formatDateTime(user.createdAt)}</dd>
              </>
            )}
          </dl>

          <p style={{ fontWeight: 600, color: 'var(--muted)', fontSize: 13.5, marginBottom: 14 }}>
            Đổi mật khẩu sẽ đăng xuất mọi thiết bị khác, thiết bị này vẫn giữ nguyên phiên đăng nhập.
          </p>

          <Button onClick={() => setChangingPassword(true)}>🔑 Đổi mật khẩu</Button>
        </div>
      </div>

      {changingPassword && (
        <ChangePasswordModal onClose={() => setChangingPassword(false)} onSuccess={setToast} />
      )}
      <Toast message={toast} onDismiss={() => setToast(null)} />
    </div>
  );
}
