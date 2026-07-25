import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import * as authApi from '../api/auth';
import { useAuth } from '../context/AuthContext';
import PasswordStrength, { checkPassword } from '../components/PasswordStrength';
import { ErrorBanner, Field } from '../components/ui';

/** Ô nhập mật khẩu kèm nút con mắt để xem/ẩn nội dung đã gõ. */
function PasswordInput({ value, onChange, placeholder, autoComplete, required, minLength }) {
  const [show, setShow] = useState(false);
  return (
    <div className="pw-input-wrap">
      <input
        className="nb-input pw-input"
        type={show ? 'text' : 'password'}
        value={value}
        onChange={onChange}
        placeholder={placeholder}
        autoComplete={autoComplete}
        required={required}
        minLength={minLength}
      />
      <button
        type="button"
        className="pw-toggle"
        onClick={() => setShow((s) => !s)}
        aria-label={show ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
        aria-pressed={show}
        tabIndex={-1}
      >
        {show ? '🙈' : '👁️'}
      </button>
    </div>
  );
}

const HEADINGS = {
  login: 'Đăng nhập',
  register: 'Tạo tài khoản',
  forgot: 'Quên mật khẩu',
};

export default function AuthPage() {
  const { login, registerRequestOtp, registerVerify } = useAuth();
  const navigate = useNavigate();

  const [mode, setMode] = useState('login');
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  // Khác null nghĩa là đang ở bước nhập OTP cho email này.
  const [pendingEmail, setPendingEmail] = useState(null);
  const [otp, setOtp] = useState('');

  const switchMode = (next) => {
    setMode(next);
    setError(null);
    setNotice(null);
    setPendingEmail(null);
    setOtp('');
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
        // Chưa tạo tài khoản: gửi OTP rồi chuyển sang bước nhập mã.
        const response = await registerRequestOtp(username.trim(), email.trim(), password);
        setPendingEmail(response.email || email.trim());
        setOtp('');
        setNotice(response.message);
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

  const handleVerifyOtp = async (e) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await registerVerify(pendingEmail, otp.trim());
      navigate('/');
    } catch (err) {
      setError(readError(err));
    } finally {
      setSubmitting(false);
    }
  };

  const handleResendOtp = async () => {
    setError(null);
    setNotice(null);
    setSubmitting(true);
    try {
      const response = await registerRequestOtp(username.trim(), email.trim(), password);
      setNotice(response.message);
    } catch (err) {
      setError(readError(err));
    } finally {
      setSubmitting(false);
    }
  };

  const submitLabel = {
    login: '🚪 Vào tủ đồ',
    register: 'Tạo tài khoản',
    forgot: '📧 Gửi link đặt lại',
  }[mode];

  const otpStep = mode === 'register' && pendingEmail;

  return (
    <div className="auth-screen">
      {/* Nửa phải: chỉ logo, cắt chéo tách khỏi phần nhập liệu bên trái. */}
      <div className="auth-brand-side" aria-hidden="true">
        <div className="auth-brand-name">
          Wear<span>Wise</span>
        </div>
        <p className="auth-brand-tag">Tủ đồ thông minh của bạn</p>
      </div>
      <span className="auth-diagonal" aria-hidden="true" />

      {/* Nửa trái: khu vực nhập liệu */}
      <div className="auth-form-side">
        <div className={`auth-form-inner ${mode === 'register' && !otpStep ? 'auth-form-inner--wide' : ''}`}>
          <div className="auth-brand-name auth-brand-name--mobile">
            Wear<span>Wise</span>
          </div>

          <h1 className="auth-heading">{otpStep ? 'Xác nhận email' : HEADINGS[mode]}</h1>

          <ErrorBanner error={error} onDismiss={() => setError(null)} />
          {notice && <div className="notice-banner">📬 {notice}</div>}

          {mode === 'forgot' && (
            <p className="auth-hint">
              Nhập email đã dùng khi đăng ký. Chúng tôi sẽ gửi link đặt lại mật khẩu (hiệu lực 30 phút).
            </p>
          )}

          {otpStep && (
            <form onSubmit={handleVerifyOtp} className="auth-form">
              <p className="auth-hint">
                Chúng tôi đã gửi mã 6 số tới <strong>{pendingEmail}</strong>. Nhập mã để hoàn tất đăng ký.
              </p>
              <Field label="Mã xác nhận (OTP)">
                <input
                  className="nb-input auth-otp-input"
                  value={otp}
                  onChange={(e) => setOtp(e.target.value.replace(/\D/g, '').slice(0, 6))}
                  placeholder="______"
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  maxLength={6}
                  required
                  autoFocus
                />
              </Field>
              <button
                type="submit"
                className="nb-btn nb-btn--pink auth-submit"
                disabled={submitting || otp.length !== 6}
              >
                {submitting ? 'Đang xử lý...' : 'Xác nhận & tạo tài khoản'}
              </button>
            </form>
          )}

          {otpStep && (
            <div className="auth-alt">
              <button type="button" className="auth-link" onClick={handleResendOtp} disabled={submitting}>
                Gửi lại mã
              </button>
              <button
                type="button"
                className="auth-link"
                onClick={() => {
                  setPendingEmail(null);
                  setOtp('');
                  setError(null);
                  setNotice(null);
                }}
              >
                ← Sửa thông tin
              </button>
            </div>
          )}

          {!otpStep && (
          <>
          <form
            onSubmit={handleSubmit}
            className={`auth-form ${mode === 'register' ? 'auth-form--grid' : ''}`}
          >
            {mode !== 'forgot' && (
              <Field label={mode === 'login' ? 'Tên đăng nhập hoặc email' : 'Tên đăng nhập'}>
                <input
                  className="nb-input"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder={mode === 'login' ? 'chien.nguyen hoặc ban@gmail.com' : 'chien.nguyen'}
                  autoComplete="username"
                  required
                />
              </Field>
            )}

            {(mode === 'register' || mode === 'forgot') && (
              <Field label="Email">
                <input
                  className="nb-input"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="ban@gmail.com"
                  autoComplete="email"
                  required
                />
              </Field>
            )}

            {mode !== 'forgot' && (
              <Field label="Mật khẩu">
                <PasswordInput
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
              <Field label="Nhập lại mật khẩu">
                <PasswordInput
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  placeholder="••••••••"
                  autoComplete="new-password"
                  required
                />
              </Field>
            )}

            {mode === 'register' && (
              // Ô có chiều cao cố định để thanh đo độ mạnh xuất hiện/biến mất không đẩy layout.
              <div className="auth-pw-slot">
                <PasswordStrength password={password} />
              </div>
            )}

            <button
              type="submit"
              className={`nb-btn auth-submit ${mode === 'register' ? 'nb-btn--pink' : 'nb-btn--primary'}`}
              disabled={submitting}
            >
              {submitting ? 'Đang xử lý...' : submitLabel}
            </button>
          </form>

          <div className="auth-alt">
            {mode === 'login' && (
              <>
                <button type="button" className="auth-link" onClick={() => switchMode('forgot')}>
                  Quên mật khẩu?
                </button>
                <p className="auth-alt-line">
                  Chưa có tài khoản?{' '}
                  <button type="button" className="auth-link auth-link--strong" onClick={() => switchMode('register')}>
                    Đăng ký
                  </button>
                </p>
              </>
            )}

            {mode === 'register' && (
              <p className="auth-alt-line">
                Đã có tài khoản?{' '}
                <button type="button" className="auth-link auth-link--strong" onClick={() => switchMode('login')}>
                  Đăng nhập
                </button>
              </p>
            )}

            {mode === 'forgot' && (
              <button type="button" className="auth-link" onClick={() => switchMode('login')}>
                ← Quay lại đăng nhập
              </button>
            )}
          </div>
          </>
          )}
        </div>
      </div>
    </div>
  );
}
