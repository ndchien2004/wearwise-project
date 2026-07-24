import { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import PasswordStrength, { checkPassword } from './PasswordStrength';
import { Button, ErrorBanner, Field, Modal } from './ui';

export default function ChangePasswordModal({ onClose, onSuccess }) {
  const { changePassword } = useAuth();

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);

    if (newPassword !== confirmPassword) {
      setError('Mật khẩu nhập lại không khớp.');
      return;
    }

    if (!checkPassword(newPassword).meetsPolicy) {
      setError('Mật khẩu phải có ít nhất 8 ký tự, gồm cả chữ và số, không chứa khoảng trắng.');
      return;
    }

    setSubmitting(true);
    try {
      await changePassword(currentPassword, newPassword);
      onSuccess?.('Đã đổi mật khẩu. Các thiết bị khác đã bị đăng xuất.');
      onClose();
    } catch (err) {
      const fieldErrors = Object.values(err.fieldErrors || {});
      setError(fieldErrors.length > 0 ? fieldErrors.join(' ') : err.message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal title="🔑 Đổi mật khẩu" onClose={onClose}>
      <ErrorBanner error={error} onDismiss={() => setError(null)} />

      <form onSubmit={handleSubmit}>
        <Field label="Mật khẩu hiện tại">
          <input
            className="nb-input"
            type="password"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
        </Field>

        <Field label="Mật khẩu mới">
          <input
            className="nb-input"
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            autoComplete="new-password"
            required
            minLength={8}
          />
        </Field>

        <PasswordStrength password={newPassword} />

        <Field label="Nhập lại mật khẩu mới">
          <input
            className="nb-input"
            type="password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            autoComplete="new-password"
            required
          />
        </Field>

        <p className="auth-hint">
          Sau khi đổi, mọi phiên đăng nhập trên thiết bị khác sẽ bị đăng xuất. Thiết bị này vẫn giữ nguyên.
        </p>

        <div className="nb-modal-actions">
          <Button onClick={onClose} disabled={submitting}>
            Hủy
          </Button>
          <button type="submit" className="nb-btn nb-btn--primary" disabled={submitting}>
            {submitting ? 'Đang lưu...' : 'Lưu mật khẩu mới'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
