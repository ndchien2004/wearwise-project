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

/** Số nút trang tối đa hiển thị cùng lúc; nhiều hơn thì rút gọn bằng dấu "…". */
const PAGE_WINDOW = 7;

/**
 * Dãy số trang quanh trang hiện tại, luôn giữ trang đầu và trang cuối.
 * Trả về mảng số, xen kẽ chuỗi '…' ở chỗ bị cắt.
 */
function pageWindow(page, pageCount) {
  if (pageCount <= PAGE_WINDOW) {
    return Array.from({ length: pageCount }, (_, i) => i);
  }

  const last = pageCount - 1;
  const side = Math.floor((PAGE_WINDOW - 3) / 2); // trừ trang đầu, trang cuối và trang hiện tại
  let start = Math.max(1, page - side);
  let end = Math.min(last - 1, page + side);

  // Giữ cửa sổ luôn đủ rộng khi trang hiện tại nằm sát hai đầu.
  if (page - side < 1) end = Math.min(last - 1, end + (1 - (page - side)));
  if (page + side > last - 1) start = Math.max(1, start - (page + side - (last - 1)));

  const pages = [0];
  if (start > 1) pages.push('…');
  for (let p = start; p <= end; p++) pages.push(p);
  if (end < last - 1) pages.push('…');
  pages.push(last);

  return pages;
}

/**
 * Phân trang: nút ← số trang → . `page` là 0-based.
 *
 * Nhật ký kiểm toán có thể lên tới hàng trăm trang, nên danh sách số trang được rút gọn thay vì
 * đổ hết ra — bản cũ render mỗi trang một nút.
 */
export function Pagination({ page, pageCount, onChange }) {
  if (pageCount <= 1) return null;
  const pages = pageWindow(page, pageCount);
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
      {pages.map((p, index) =>
        p === '…' ? (
          <span key={`gap-${index}`} className="pagination-gap" aria-hidden="true">
            …
          </span>
        ) : (
          <button
            key={p}
            type="button"
            className={`nb-btn nb-btn--sm ${p === page ? 'nb-btn--dark' : ''}`}
            onClick={() => onChange(p)}
            aria-current={p === page ? 'page' : undefined}
          >
            {p + 1}
          </button>
        )
      )}
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
