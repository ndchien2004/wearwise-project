import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Button, ErrorBanner, Field } from '../components/ui';

export default function AuthPage() {
  const { login, register } = useAuth();
  const navigate = useNavigate();

  const [mode, setMode] = useState('login');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);

    if (mode === 'register' && password !== confirmPassword) {
      setError('Mật khẩu nhập lại không khớp.');
      return;
    }

    setSubmitting(true);
    try {
      if (mode === 'login') {
        await login(username.trim(), password);
      } else {
        await register(username.trim(), password);
      }
      navigate('/');
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
        <h1 className="auth-logo">
          👕 Wear<span style={{ color: '#e8590c' }}>Wise</span>
        </h1>
        <p style={{ textAlign: 'center', fontWeight: 600, color: 'var(--muted)' }}>
          Tủ đồ thông minh của bạn
        </p>

        <div className="auth-tabs">
          <Button
            variant={mode === 'login' ? 'primary' : undefined}
            onClick={() => { setMode('login'); setError(null); }}
          >
            Đăng nhập
          </Button>
          <Button
            variant={mode === 'register' ? 'pink' : undefined}
            onClick={() => { setMode('register'); setError(null); }}
          >
            Đăng ký
          </Button>
        </div>

        <ErrorBanner error={error} onDismiss={() => setError(null)} />

        <form onSubmit={handleSubmit}>
          <Field label="Tên đăng nhập">
            <input
              className="nb-input"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="vd: chien.nguyen"
              autoComplete="username"
              required
            />
          </Field>

          <Field label="Mật khẩu">
            <input
              className="nb-input"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              required
              minLength={mode === 'register' ? 6 : undefined}
            />
          </Field>

          {mode === 'register' && (
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
          )}

          <button
            type="submit"
            className={`nb-btn ${mode === 'login' ? 'nb-btn--primary' : 'nb-btn--pink'}`}
            style={{ width: '100%', marginTop: 8 }}
            disabled={submitting}
          >
            {submitting ? 'Đang xử lý...' : mode === 'login' ? '🚪 Vào tủ đồ' : '✨ Tạo tài khoản'}
          </button>
        </form>
      </div>
    </div>
  );
}
