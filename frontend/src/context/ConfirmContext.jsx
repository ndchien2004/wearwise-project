import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';

// Modal xác nhận neobrutalism thay cho window.confirm của trình duyệt.
// Dùng: const confirm = useConfirm();
//       if (await confirm({ title, message, confirmLabel, danger })) { ... }

const ConfirmContext = createContext(null);

export function ConfirmProvider({ children }) {
  const [dialog, setDialog] = useState(null);
  const resolverRef = useRef(null);

  const confirm = useCallback((options) => {
    return new Promise((resolve) => {
      resolverRef.current = resolve;
      setDialog({
        title: options.title ?? 'Xác nhận',
        message: options.message ?? 'Bạn có chắc chắn không?',
        confirmLabel: options.confirmLabel ?? 'Đồng ý',
        cancelLabel: options.cancelLabel ?? 'Hủy',
        danger: Boolean(options.danger),
      });
    });
  }, []);

  const close = useCallback((result) => {
    setDialog(null);
    if (resolverRef.current) {
      resolverRef.current(result);
      resolverRef.current = null;
    }
  }, []);

  useEffect(() => {
    if (!dialog) return undefined;
    const onKey = (e) => {
      if (e.key === 'Escape') close(false);
      if (e.key === 'Enter') close(true);
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [dialog, close]);

  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      {dialog && (
        <div
          className="nb-modal-overlay"
          onMouseDown={(e) => {
            if (e.target === e.currentTarget) close(false);
          }}
        >
          <div className="nb-modal confirm-dialog">
            <div className="confirm-emoji">{dialog.danger ? '🗑️' : '🤔'}</div>
            <h3 className="nb-modal-title" style={{ textAlign: 'center' }}>{dialog.title}</h3>
            <p className="confirm-message">{dialog.message}</p>
            <div className="confirm-actions">
              <button type="button" className="nb-btn" onClick={() => close(false)} autoFocus>
                {dialog.cancelLabel}
              </button>
              <button
                type="button"
                className={`nb-btn ${dialog.danger ? 'nb-btn--danger' : 'nb-btn--primary'}`}
                onClick={() => close(true)}
              >
                {dialog.confirmLabel}
              </button>
            </div>
          </div>
        </div>
      )}
    </ConfirmContext.Provider>
  );
}

export function useConfirm() {
  const confirm = useContext(ConfirmContext);
  if (!confirm) {
    throw new Error('useConfirm phải được dùng bên trong ConfirmProvider');
  }
  return confirm;
}
