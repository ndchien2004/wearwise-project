import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import * as authApi from '../api/auth';
import PasswordStrength, { checkPassword } from '../components/PasswordStrength';
import { ErrorBanner, Field, Loading } from '../components/ui';

export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = searchParams.get('token') || '';

  const [tokenState, setTokenState] = useState('checking');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  // Kiểm tra link trước khi hiện form, để người dùng không gõ mật khẩu rồi mới biết link đã hết hạn.
  useEffect(() => {
    let cancelled = false;

    if (!token) {
      setTokenState('invalid');
      return undefined;
    }

    authApi
      .validateResetToken(token)
      .then((response) => {
        if (!cancelled) setTokenState(response.valid ? 'valid' : 'invalid');
      })
      .catch(() => {
        if (!cancelled) setTokenState('invalid');
      });

    return () => {
      cancelled = true;
    };
  }, [token]);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);

    if (password !== confirmPassword) {
      setError('Mật khẩu nhập lại không khớp.');
      return;
    }

    if (!checkPassword(password).meetsPolicy) {
      setError('Mật khẩu phải có ít nhất 8 ký tự, gồm cả chữ và số, không chứa khoảng trắng.');
      return;
    }

    setSubmitting(true);
    try {
      await authApi.resetPassword(token, password);
      setDone(true);
      setTimeout(() => navigate('/login'), 2500);
    } catch (err) {
      const fieldErrors = Object.values(err.fieldErrors || {});
      setError(fieldErrors.length > 0 ? fieldErrors.join(' ') : err.message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="auth-wrap">
      <div className="nb-card auth-card">
        <h1 className="auth-logo">🔑 Đặt lại mật khẩu</h1>

        {tokenState === 'checking' && <Loading>Đang kiểm tra link...</Loading>}

        {tokenState === 'invalid' && (
          <>
            <ErrorBanner error="Link đặt lại mật khẩu không hợp lệ hoặc đã hết hạn." />
            <p className="auth-hint">
              Link chỉ dùng được một lần và hết hạn sau 30 phút. Hãy yêu cầu link mới từ trang đăng nhập.
            </p>
            <Link to="/login" className="nb-btn nb-btn--primary" style={{ width: '100%', marginTop: 8 }}>
              ← Về trang đăng nhập
            </Link>
          </>
        )}

        {tokenState === 'valid' && done && (
          <>
            <div className="notice-banner">✅ Đổi mật khẩu thành công! Đang chuyển về trang đăng nhập...</div>
            <Link to="/login" className="nb-btn nb-btn--primary" style={{ width: '100%', marginTop: 12 }}>
              Đăng nhập ngay
            </Link>
          </>
        )}

        {tokenState === 'valid' && !done && (
          <>
            <p className="auth-hint">Chọn mật khẩu mới cho tài khoản của bạn.</p>
            <ErrorBanner error={error} onDismiss={() => setError(null)} />

            <form onSubmit={handleSubmit}>
              <Field label="Mật khẩu mới">
                <input
                  className="nb-input"
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  autoComplete="new-password"
                  required
                  minLength={8}
                />
              </Field>

              <PasswordStrength password={password} />

              <Field label="Nhập lại mật khẩu mới">
                <input
                  className="nb-input"
                  type="password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  placeholder="••••••••"
                  autoComplete="new-password"
                  required
                />
              </Field>

              <button
                type="submit"
                className="nb-btn nb-btn--primary"
                style={{ width: '100%', marginTop: 8 }}
                disabled={submitting}
              >
                {submitting ? 'Đang lưu...' : '💾 Lưu mật khẩu mới'}
              </button>
            </form>
          </>
        )}
      </div>
    </div>
  );
}
