import { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { formatDateTime } from '../utils/date';
import { Button, ErrorBanner, Field, Modal } from './ui';

export default function AccountModal({ onClose, onSuccess }) {
  const { user, username, updateEmail } = useAuth();

  const [email, setEmail] = useState(user?.email || '');
  const [currentPassword, setCurrentPassword] = useState('');
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const hasEmail = Boolean(user?.email);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);

    try {
      await updateEmail(currentPassword, email.trim());
      onSuccess?.(hasEmail ? 'Đã cập nhật email.' : 'Đã thêm email cho tài khoản.');
      onClose();
    } catch (err) {
      const fieldErrors = Object.values(err.fieldErrors || {});
      setError(fieldErrors.length > 0 ? fieldErrors.join(' ') : err.message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal title="👤 Tài khoản" onClose={onClose}>
      <dl className="account-facts">
        <dt>Tên đăng nhập</dt>
        <dd>{username}</dd>
        <dt>Email</dt>
        <dd>{user?.email || <span style={{ color: 'var(--muted)' }}>chưa có</span>}</dd>
        {user?.createdAt && (
          <>
            <dt>Ngày tạo</dt>
            <dd>{formatDateTime(user.createdAt)}</dd>
          </>
        )}
      </dl>

      {!hasEmail && (
        <div className="notice-banner" style={{ background: 'var(--yellow)' }}>
          ⚠️ Tài khoản chưa có email nên chưa dùng được chức năng quên mật khẩu. Thêm email ngay bên dưới.
        </div>
      )}

      <ErrorBanner error={error} onDismiss={() => setError(null)} />

      <form onSubmit={handleSubmit}>
        <Field label={hasEmail ? 'Email mới' : 'Email'}>
          <input
            className="nb-input"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="vd: ban@gmail.com"
            autoComplete="email"
            required
          />
        </Field>

        <Field label="Mật khẩu hiện tại (để xác nhận)">
          <input
            className="nb-input"
            type="password"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
        </Field>

        <p className="auth-hint">
          Email là nơi nhận link đặt lại mật khẩu, nên phải nhập lại mật khẩu để đổi.
        </p>

        <div className="nb-modal-actions">
          <Button onClick={onClose} disabled={submitting}>
            Hủy
          </Button>
          <button type="submit" className="nb-btn nb-btn--primary" disabled={submitting}>
            {submitting ? 'Đang lưu...' : hasEmail ? 'Cập nhật email' : 'Thêm email'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
