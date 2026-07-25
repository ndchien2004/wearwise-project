import { useEffect, useRef, useState } from 'react';
import * as authApi from '../api/auth';
import * as itemsApi from '../api/clothingItems';
import { getStatistics } from '../api/statistics';
import AvatarCropModal from '../components/AvatarCropModal';
import BarChart from '../components/BarChart';
import ChangePasswordModal from '../components/ChangePasswordModal';
import { Badge, Button, EmptyState, Loading, Toast } from '../components/ui';
import { useAuth } from '../context/AuthContext';
import { useConfirm } from '../context/ConfirmContext';
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

const MAX_AVATAR_BYTES = 10 * 1024 * 1024;
const ALLOWED_AVATAR_TYPES = ['image/jpeg', 'image/jpg', 'image/png'];

export default function ProfilePage() {
  const { user, username, updateUser } = useAuth();
  const confirm = useConfirm();

  const [changingPassword, setChangingPassword] = useState(false);
  const [toast, setToast] = useState(null);
  const [stats, setStats] = useState(null);
  const [leastWorn, setLeastWorn] = useState([]);
  const [statsFailed, setStatsFailed] = useState(false);
  const [uploadingAvatar, setUploadingAvatar] = useState(false);
  const [cropFile, setCropFile] = useState(null);
  const avatarInputRef = useRef(null);

  const hasEmail = Boolean(user?.email);

  // Toast dùng chung: object { message, variant } để báo cả thành công lẫn lỗi.
  const showToast = (message, variant = 'success') => setToast({ message, variant });

  // Chọn tệp xong: mở modal căn chỉnh/crop thay vì tải thẳng lên.
  const handleAvatarSelected = (e) => {
    const file = e.target.files?.[0];
    e.target.value = ''; // cho phép chọn lại cùng một tệp lần sau
    if (!file) return;

    if (!ALLOWED_AVATAR_TYPES.includes(file.type)) {
      showToast('Ảnh đại diện phải ở định dạng JPG hoặc PNG.', 'error');
      return;
    }
    if (file.size > MAX_AVATAR_BYTES) {
      showToast('Ảnh quá lớn (tối đa 10MB). Hãy chọn ảnh nhẹ hơn.', 'error');
      return;
    }

    setCropFile(file);
  };

  // Người dùng đã căn xong trong modal: tải ảnh vuông đã cắt lên.
  const handleCropConfirm = async (blob) => {
    setCropFile(null);
    setUploadingAvatar(true);
    try {
      const profile = await authApi.uploadAvatar(blob);
      updateUser(profile);
      showToast('Đã cập nhật ảnh đại diện.');
    } catch (err) {
      showToast(err.message || 'Tải ảnh đại diện thất bại. Vui lòng thử lại.', 'error');
    } finally {
      setUploadingAvatar(false);
    }
  };

  const handleRemoveAvatar = async () => {
    const ok = await confirm({
      title: 'Gỡ ảnh đại diện',
      message: 'Bạn có chắc muốn gỡ ảnh đại diện hiện tại không?',
      confirmLabel: 'Gỡ ảnh',
      danger: true,
    });
    if (!ok) return;

    setUploadingAvatar(true);
    try {
      const profile = await authApi.removeAvatar();
      updateUser(profile);
      showToast('Đã gỡ ảnh đại diện.');
    } catch (err) {
      showToast(err.message || 'Không gỡ được ảnh đại diện. Vui lòng thử lại.', 'error');
    } finally {
      setUploadingAvatar(false);
    }
  };

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
        <div className="profile-avatar-col">
          <div className="profile-avatar-wrap">
            <div className="profile-avatar">
              {user?.avatarUrl ? (
                <img src={user.avatarUrl} alt="Ảnh đại diện" />
              ) : (
                (username || '?').charAt(0).toUpperCase()
              )}
            </div>
            <button
              type="button"
              className="profile-avatar-edit"
              onClick={() => avatarInputRef.current?.click()}
              disabled={uploadingAvatar}
              title="Đổi ảnh đại diện"
              aria-label="Đổi ảnh đại diện"
            >
              {uploadingAvatar ? '⏳' : '📷'}
            </button>
            <input
              ref={avatarInputRef}
              type="file"
              accept="image/png,image/jpeg"
              onChange={handleAvatarSelected}
              hidden
            />
          </div>
          {user?.avatarUrl && (
            <button
              type="button"
              className="auth-link profile-avatar-remove"
              onClick={handleRemoveAvatar}
              disabled={uploadingAvatar}
            >
              Gỡ ảnh
            </button>
          )}
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

        <Button className="profile-hero-action" onClick={() => setChangingPassword(true)}>
          🔑 Đổi mật khẩu
        </Button>
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

      {cropFile && (
        <AvatarCropModal
          file={cropFile}
          onCancel={() => setCropFile(null)}
          onConfirm={handleCropConfirm}
        />
      )}
      {changingPassword && (
        <ChangePasswordModal
          onClose={() => setChangingPassword(false)}
          onSuccess={(msg) => showToast(msg)}
        />
      )}
      <Toast message={toast?.message} variant={toast?.variant} onDismiss={() => setToast(null)} />
    </div>
  );
}
