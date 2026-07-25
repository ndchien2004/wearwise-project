import { useEffect, useRef, useState } from 'react';

/**
 * Menu điều hướng: mở đè lên toàn màn hình, làm mờ nền phía sau. Các mục dàn theo danh sách
 * dọc bình thường từ trên xuống — không còn kiểu cuộn 3D canh giữa. Nếu số mục nhiều hơn
 * chiều cao màn hình thì vùng danh sách tự cuộn.
 */
export default function GameMenu({ items, onPick, onClose }) {
  // Mở menu ở đúng mục đang xem (giữ state), không nhảy về đầu danh sách.
  const [focus, setFocus] = useState(() => Math.max(0, items.findIndex((item) => item.current)));
  const itemRefs = useRef([]);

  const step = (delta) =>
    setFocus((i) => Math.min(items.length - 1, Math.max(0, i + delta)));

  // Khóa cuộn nền trong lúc menu mở.
  useEffect(() => {
    const previous = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = previous;
    };
  }, []);

  // Chọn bằng bàn phím thì kéo mục đó vào tầm nhìn nếu danh sách đang phải cuộn.
  useEffect(() => {
    itemRefs.current[focus]?.scrollIntoView({ block: 'nearest' });
  }, [focus]);

  useEffect(() => {
    const onKey = (e) => {
      switch (e.key) {
        case 'Escape':
          onClose();
          break;
        case 'ArrowDown':
        case 'ArrowRight':
          e.preventDefault();
          step(1);
          break;
        case 'ArrowUp':
        case 'ArrowLeft':
          e.preventDefault();
          step(-1);
          break;
        case 'Home':
          e.preventDefault();
          setFocus(0);
          break;
        case 'End':
          e.preventDefault();
          setFocus(items.length - 1);
          break;
        case 'Enter':
        case ' ':
          e.preventDefault();
          onPick(items[focus]);
          break;
        default:
          break;
      }
    };

    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [items, focus, onClose, onPick]);

  // Bấm ra vùng nền (ngoài các nút) thì đóng menu.
  const closeOnBackdrop = (e) => {
    if (e.target === e.currentTarget) onClose();
  };

  return (
    <div
      className="game-menu"
      role="dialog"
      aria-modal="true"
      aria-label="Menu điều hướng"
      onMouseDown={closeOnBackdrop}
    >
      <div className="game-menu-head">
        <span className="game-menu-logo">
          Wear<span>Wise</span>
        </span>
        <button type="button" className="game-menu-close" onClick={onClose} aria-label="Đóng menu">
          ✕
        </button>
      </div>

      <nav className="game-menu-list-wrap" onMouseDown={closeOnBackdrop}>
        <ul className="game-menu-list">
          {items.map((item, i) => (
            <li key={item.key}>
              <button
                type="button"
                ref={(el) => {
                  itemRefs.current[i] = el;
                }}
                className={[
                  'game-menu-item',
                  i === focus ? 'is-focused' : '',
                  item.danger ? 'is-danger' : '',
                  item.current ? 'is-current' : '',
                ]
                  .filter(Boolean)
                  .join(' ')}
                onFocus={() => setFocus(i)}
                onClick={() => onPick(item)}
              >
                <span className="game-menu-emoji" aria-hidden="true">{item.emoji}</span>
                <span className="game-menu-label">{item.label}</span>
                {item.current && <span className="game-menu-tag">đang xem</span>}
                {item.alert && (
                  <span className="game-menu-alert" title="Chưa có email">!</span>
                )}
                <span className="game-menu-arrow" aria-hidden="true">▸</span>
              </button>
            </li>
          ))}
        </ul>
      </nav>

      <p className="game-menu-hint">↑ ↓ để chọn · Enter để đi · Esc để đóng</p>
    </div>
  );
}
