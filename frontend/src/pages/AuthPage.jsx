import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import * as authApi from '../api/auth';
import { useAuth } from '../context/AuthContext';
import PasswordStrength, { checkPassword } from '../components/PasswordStrength';
import { Button, ErrorBanner, Field } from '../components/ui';

export default function AuthPage() {
  const { login, register } = useAuth();
  const navigate = useNavigate();

  const [mode, setMode] = useState('login');
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const switchMode = (next) => {
    setMode(next);
    setError(null);
    setNotice(null);
  };

  // Thông báo lỗi lấy thẳng từ server (đã là tiếng Việt) — không đoán lại theo mã HTTP,
  // để chỉ có một nguồn sự thật duy nhất cho nội dung lỗi.
  const readError = (err) => {
    const fieldErrors = Object.values(err.fieldErrors || {});
    return fieldErrors.length > 0 ? fieldErrors.join(' ') : err.message;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setNotice(null);

    if (mode === 'register') {
      if (password !== confirmPassword) {
        setError('Mật khẩu nhập lại không khớp.');
        return;
      }
      if (!checkPassword(password).meetsPolicy) {
        setError('Mật khẩu phải có ít nhất 8 ký tự, gồm cả chữ và số, không chứa khoảng trắng.');
        return;
      }
    }

    setSubmitting(true);
    try {
      if (mode === 'login') {
        await login(username.trim(), password);
        navigate('/');
      } else if (mode === 'register') {
        await register(username.trim(), email.trim(), password);
        navigate('/');
      } else {
        const response = await authApi.forgotPassword(email.trim());
        setNotice(response.message);
        setPassword('');
      }
    } catch (err) {
      setError(readError(err));
    } finally {
      setSubmitting(false);
    }
  };

  const submitLabel = {
    login: '🚪 Vào tủ đồ',
    register: '✨ Tạo tài khoản',
    forgot: '📧 Gửi link đặt lại',
  }[mode];

  return (
    <div className="auth-wrap">
      <div className="nb-card auth-card">
        <h1 className="auth-logo">
          👕 Wear<span style={{ color: '#e8590c' }}>Wise</span>
        </h1>
        <p style={{ textAlign: 'center', fontWeight: 600, color: 'var(--muted)' }}>
          Tủ đồ thông minh của bạn
        </p>

        <div className="auth-tabs">
          <Button
            variant={mode === 'login' ? 'primary' : undefined}
            onClick={() => switchMode('login')}
          >
            Đăng nhập
          </Button>
          <Button
            variant={mode === 'register' ? 'pink' : undefined}
            onClick={() => switchMode('register')}
          >
            Đăng ký
          </Button>
        </div>

        <ErrorBanner error={error} onDismiss={() => setError(null)} />
        {notice && <div className="notice-banner">📬 {notice}</div>}

        {mode === 'forgot' && (
          <p className="auth-hint">
            Nhập email đã dùng khi đăng ký. Chúng tôi sẽ gửi link đặt lại mật khẩu (có hiệu lực 30 phút).
          </p>
        )}

        <form onSubmit={handleSubmit}>
          {mode !== 'forgot' && (
            <Field label={mode === 'login' ? 'Tên đăng nhập hoặc email' : 'Tên đăng nhập'}>
              <input
                className="nb-input"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder={mode === 'login' ? 'vd: chien.nguyen hoặc ban@gmail.com' : 'vd: chien.nguyen'}
                autoComplete="username"
                required
              />
              {mode === 'register' && (
                <p className="auth-hint">
                  Chỉ gồm chữ không dấu, số và các ký tự . _ - — không khoảng trắng.
                </p>
              )}
            </Field>
          )}

          {(mode === 'register' || mode === 'forgot') && (
            <Field label="Email">
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
          )}

          {mode !== 'forgot' && (
            <Field label="Mật khẩu">
              <input
                className="nb-input"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                required
                minLength={mode === 'register' ? 8 : undefined}
              />
            </Field>
          )}

          {mode === 'register' && (
            <>
              <PasswordStrength password={password} />
              <Field label="Nhập lại mật khẩu">
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
            </>
          )}

          <button
            type="submit"
            className={`nb-btn ${mode === 'register' ? 'nb-btn--pink' : 'nb-btn--primary'}`}
            style={{ width: '100%', marginTop: 8 }}
            disabled={submitting}
          >
            {submitting ? 'Đang xử lý...' : submitLabel}
          </button>
        </form>

        <div className="auth-links">
          {mode === 'forgot' ? (
            <button type="button" className="auth-link" onClick={() => switchMode('login')}>
              ← Quay lại đăng nhập
            </button>
          ) : (
            <button type="button" className="auth-link" onClick={() => switchMode('forgot')}>
              Quên mật khẩu?
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
