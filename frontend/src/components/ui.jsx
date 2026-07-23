import { useEffect } from 'react';

export function Button({ variant, size, className = '', children, ...props }) {
  const classes = [
    'nb-btn',
    variant ? `nb-btn--${variant}` : '',
    size ? `nb-btn--${size}` : '',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <button type="button" className={classes} {...props}>
      {children}
    </button>
  );
}

export function Badge({ color, children }) {
  return <span className={`nb-badge ${color ? `nb-badge--${color}` : ''}`}>{children}</span>;
}

export function Field({ label, className = '', children }) {
  return (
    <div className={`nb-field ${className}`.trim()}>
      {label && <label className="nb-label">{label}</label>}
      {children}
    </div>
  );
}

export function Modal({ title, wide, onClose, children }) {
  useEffect(() => {
    const onKey = (e) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <div
      className="nb-modal-overlay"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className={`nb-modal ${wide ? 'nb-modal--wide' : ''}`}>
        <div className="nb-modal-header">
          <h3 className="nb-modal-title">{title}</h3>
          <Button size="icon" onClick={onClose} aria-label="Đóng">
            ✕
          </Button>
        </div>
        {children}
      </div>
    </div>
  );
}

/** Phân trang đơn giản: nút ← số trang → . page là 0-based. */
export function Pagination({ page, pageCount, onChange }) {
  if (pageCount <= 1) return null;
  const pages = Array.from({ length: pageCount }, (_, i) => i);
  return (
    <div className="pagination">
      <button
        type="button"
        className="nb-btn nb-btn--sm"
        disabled={page === 0}
        onClick={() => onChange(page - 1)}
        aria-label="Trang trước"
      >
        ←
      </button>
      {pages.map((p) => (
        <button
          key={p}
          type="button"
          className={`nb-btn nb-btn--sm ${p === page ? 'nb-btn--dark' : ''}`}
          onClick={() => onChange(p)}
        >
          {p + 1}
        </button>
      ))}
      <button
        type="button"
        className="nb-btn nb-btn--sm"
        disabled={page >= pageCount - 1}
        onClick={() => onChange(page + 1)}
        aria-label="Trang sau"
      >
        →
      </button>
    </div>
  );
}

export function EmptyState({ emoji = '🗂️', children }) {
  return (
    <div className="empty-state">
      <span className="empty-emoji">{emoji}</span>
      <p>{children}</p>
    </div>
  );
}

export function Loading({ children = 'Đang tải...' }) {
  return <div className="loading-box">{children}</div>;
}

/** Thông báo nổi, tự ẩn sau `duration` ms. variant: 'success' | 'error' | 'info'. */
export function Toast({ message, variant = 'success', duration = 3000, onDismiss }) {
  useEffect(() => {
    if (!message) return undefined;
    const timer = setTimeout(() => onDismiss?.(), duration);
    return () => clearTimeout(timer);
  }, [message, duration, onDismiss]);

  if (!message) return null;

  const emoji = variant === 'error' ? '⚠️' : variant === 'info' ? 'ℹ️' : '✅';
  return (
    <div className={`nb-toast nb-toast--${variant}`} role="status">
      <span>{emoji} {message}</span>
      {onDismiss && (
        <button type="button" className="nb-toast-close" onClick={onDismiss} aria-label="Đóng">
          ✕
        </button>
      )}
    </div>
  );
}

export function ErrorBanner({ error, onDismiss }) {
  if (!error) return null;
  return (
    <div className="error-banner">
      ⚠️ {error}
      {onDismiss && (
        <button
          type="button"
          onClick={onDismiss}
          style={{ float: 'right', background: 'none', border: 'none', cursor: 'pointer', fontWeight: 900 }}
        >
          ✕
        </button>
      )}
    </div>
  );
}
