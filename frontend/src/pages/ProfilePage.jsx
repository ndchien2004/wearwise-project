import { useEffect, useState } from 'react';
import * as itemsApi from '../api/clothingItems';
import { getStatistics } from '../api/statistics';
import BarChart from '../components/BarChart';
import ChangePasswordModal from '../components/ChangePasswordModal';
import { Badge, Button, EmptyState, Loading, Toast } from '../components/ui';
import { useAuth } from '../context/AuthContext';
import {
  CATEGORY_EMOJIS,
  CATEGORY_LABELS,
  CONDITION_LABELS,
  SEASON_LABELS,
  STATUS_LABELS,
  STYLE_LABELS,
} from '../utils/labels';
import { daysSince, formatDateTime } from '../utils/date';

// Bảng màu chart đã kiểm tra CVD-safe (deutan ΔE 19.7, contrast ≥ 3:1 trên nền trắng).
const CHART_CATEGORY_COLORS = {
  SHIRT: '#1971c2',
  PANTS: '#7048e8',
  SHOES: '#e8590c',
  JACKET: '#2f9e44',
  ACCESSORY: '#d6336c',
};

const SINGLE_HUE = {
  style: '#7048e8',
  season: '#1971c2',
  topWorn: '#e8590c',
};

const STATUS_CHART_COLORS = {
  AVAILABLE: '#2f9e44',
  LAUNDRY: '#e8590c',
  UNAVAILABLE: '#868e96',
};

const CONDITION_CHART_COLORS = {
  GOOD: '#2f9e44',
  DAMAGED: '#c92a2a',
};

function mapToRows(map, labels, colorFor) {
  return Object.keys(labels).map((key) => ({
    key,
    label: labels[key],
    value: map?.[key] ?? 0,
    color: colorFor(key),
  }));
}

export default function ProfilePage() {
  const { user, username } = useAuth();

  const [changingPassword, setChangingPassword] = useState(false);
  const [toast, setToast] = useState(null);
  const [stats, setStats] = useState(null);
  const [leastWorn, setLeastWorn] = useState([]);
  const [statsFailed, setStatsFailed] = useState(false);

  const hasEmail = Boolean(user?.email);

  // Thống kê nằm cùng trang tài khoản; lỗi thì chỉ ẩn khối thống kê, phần tài khoản vẫn dùng được.
  useEffect(() => {
    Promise.all([getStatistics(), itemsApi.getLeastWornItems(6)])
      .then(([s, least]) => {
        setStats(s);
        setLeastWorn(least);
      })
      .catch(() => setStatsFailed(true));
  }, []);

  const topWorn = (stats?.topWornItems || []).filter((i) => (i.wearCount ?? 0) > 0);

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
                <p className="muted-note" style={{ marginTop: 12 }}>
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

          <p className="muted-note" style={{ marginBottom: 14 }}>
            Đổi mật khẩu sẽ đăng xuất mọi thiết bị khác, thiết bị này vẫn giữ nguyên phiên đăng nhập.
          </p>

          <Button onClick={() => setChangingPassword(true)}>🔑 Đổi mật khẩu</Button>
        </div>
      </div>

      {/* Thống kê tủ đồ gộp vào đây thay vì đứng riêng một mục ở thanh bên. */}
      <div className="section-divider">
        <h2 className="section-heading">📊 Thống kê tủ đồ</h2>
      </div>

      {statsFailed ? (
        <EmptyState emoji="📉">Chưa lấy được số liệu thống kê. Thử tải lại trang nhé!</EmptyState>
      ) : !stats ? (
        <Loading>Đang tính toán thống kê...</Loading>
      ) : (
        <>
          <div className="two-col">
            <div className="nb-card chart-section">
              <h3 className="chart-title">👕 Theo danh mục</h3>
              <BarChart
                data={mapToRows(stats.clothingItemsByCategory, CATEGORY_LABELS, (k) => CHART_CATEGORY_COLORS[k])}
              />
            </div>

            <div className="nb-card chart-section">
              <h3 className="chart-title">🎨 Theo phong cách</h3>
              <BarChart data={mapToRows(stats.clothingItemsByStyle, STYLE_LABELS, () => SINGLE_HUE.style)} />
            </div>

            <div className="nb-card chart-section">
              <h3 className="chart-title">🍀 Theo mùa</h3>
              <BarChart data={mapToRows(stats.clothingItemsBySeason, SEASON_LABELS, () => SINGLE_HUE.season)} />
            </div>

            <div className="nb-card chart-section">
              <h3 className="chart-title">🚦 Theo trạng thái &amp; tình trạng</h3>
              <BarChart
                data={[
                  ...mapToRows(stats.clothingItemsByStatus, STATUS_LABELS, (k) => STATUS_CHART_COLORS[k]),
                  ...mapToRows(stats.clothingItemsByCondition, CONDITION_LABELS, (k) => CONDITION_CHART_COLORS[k]),
                ]}
              />
            </div>
          </div>

          <div className="two-col">
            <div className="nb-card chart-section">
              <h3 className="chart-title">🏆 Mặc nhiều nhất</h3>
              {topWorn.length === 0 ? (
                <EmptyState emoji="👣">Chưa có lượt mặc nào được ghi nhận.</EmptyState>
              ) : (
                <BarChart
                  data={topWorn.map((item) => ({
                    key: item.id,
                    label: `${CATEGORY_EMOJIS[item.category] || ''} ${item.name}`,
                    value: item.wearCount ?? 0,
                    color: SINGLE_HUE.topWorn,
                  }))}
                />
              )}
            </div>

            <div className="nb-card chart-section">
              <h3 className="chart-title">🕸️ Lâu chưa đụng tới</h3>
              <p className="muted-note" style={{ marginBottom: 12 }}>
                Cân nhắc đem pass hoặc quyên góp những món này nhé!
              </p>
              {leastWorn.length === 0 ? (
                <EmptyState emoji="🧺">Tủ đồ đang trống.</EmptyState>
              ) : (
                <div className="stack-list">
                  {leastWorn.map((item) => {
                    const days = daysSince(item.lastWornAt);
                    return (
                      <div key={item.id} className="least-worn-row">
                        <div className="least-worn-main">
                          <strong className="least-worn-name">
                            {CATEGORY_EMOJIS[item.category]} {item.name}
                          </strong>
                          <span className="least-worn-sub">
                            {days === null
                              ? 'Chưa mặc lần nào'
                              : `Lần cuối: ${formatDateTime(item.lastWornAt)}`}
                          </span>
                        </div>
                        <Badge color={days === null ? 'red' : 'muted'}>
                          {days === null ? 'Chưa mặc bao giờ' : `${days} ngày trước`}
                        </Badge>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </div>
        </>
      )}

      {changingPassword && (
        <ChangePasswordModal onClose={() => setChangingPassword(false)} onSuccess={setToast} />
      )}
      <Toast message={toast} onDismiss={() => setToast(null)} />
    </div>
  );
}
