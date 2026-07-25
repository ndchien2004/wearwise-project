import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';

// Ngưỡng delta cho mỗi bước chọn: chuột nảy 1 nấc ~100, trackpad bắn nhiều event nhỏ nên
// phải cộng dồn thay vì nhảy theo từng event.
const WHEEL_STEP = 40;
const WHEEL_LOCK_MS = 70;

/**
 * Menu điều hướng kiểu game: mở đè lên toàn màn hình, làm mờ nền phía sau, các mục xếp
 * chồng dạng 3D. Mục đang chọn luôn nằm chính giữa màn hình — cuộn chuột (hoặc ↑↓) kéo cả
 * băng mục trượt qua tâm màn hình chứ không cuộn theo kiểu danh sách thường.
 */
export default function GameMenu({ items, onPick, onClose }) {
  // Mở ra là bắt đầu từ mục đầu danh sách, và mục đó nằm sẵn giữa màn hình.
  const [focus, setFocus] = useState(0);
  const [trackOffset, setTrackOffset] = useState(0);
  const [ready, setReady] = useState(false);
  const overlayRef = useRef(null);
  const trackRef = useRef(null);
  const slotRefs = useRef([]);
  const wheelAcc = useRef(0);
  const wheelAt = useRef(0);

  const step = (delta) =>
    setFocus((i) => Math.min(items.length - 1, Math.max(0, i + delta)));

  // Kéo băng mục sao cho tâm mục đang chọn trùng tâm màn hình. offsetTop không bị ảnh hưởng
  // bởi transform 3D của từng mục nên đo được vị trí "phẳng" thật của nó trong băng.
  const recenter = useCallback(() => {
    const slot = slotRefs.current[focus];
    if (!slot) return;
    setTrackOffset(window.innerHeight / 2 - (slot.offsetTop + slot.offsetHeight / 2));
  }, [focus]);

  useLayoutEffect(() => {
    recenter();
  }, [recenter]);

  useEffect(() => {
    window.addEventListener('resize', recenter);
    return () => window.removeEventListener('resize', recenter);
  }, [recenter]);

  /**
   * Canh giữa một lần là chưa đủ: web font nạp theo kiểu swap nên chiều cao các mục còn đổi
   * sau khi menu đã mở, đo trước lúc đó sẽ lệch. Theo dõi kích thước băng mục để canh lại
   * mỗi khi layout thay đổi.
   */
  useEffect(() => {
    const track = trackRef.current;
    if (!track || typeof ResizeObserver === 'undefined') return undefined;
    const observer = new ResizeObserver(() => recenter());
    observer.observe(track);
    return () => observer.disconnect();
  }, [recenter]);

  // Lần canh giữa đầu tiên phải nhảy thẳng vào vị trí, chỉ bật hiệu ứng trượt từ lần sau.
  useEffect(() => {
    const id = requestAnimationFrame(() => setReady(true));
    return () => cancelAnimationFrame(id);
  }, []);

  // Khóa cuộn nền trong lúc menu mở.
  useEffect(() => {
    const previous = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = previous;
    };
  }, []);

  // React gắn onWheel ở chế độ passive nên preventDefault trong đó không ăn — phải tự gắn
  // listener non-passive để cuộn chuột đổi lựa chọn thay vì cuộn trang.
  useEffect(() => {
    const node = overlayRef.current;
    if (!node) return undefined;

    const handleWheel = (e) => {
      e.preventDefault();
      wheelAcc.current += e.deltaY;
      if (Math.abs(wheelAcc.current) < WHEEL_STEP) return;

      const now = performance.now();
      const direction = wheelAcc.current > 0 ? 1 : -1;
      wheelAcc.current = 0;
      if (now - wheelAt.current < WHEEL_LOCK_MS) return;
      wheelAt.current = now;
      step(direction);
    };

    node.addEventListener('wheel', handleWheel, { passive: false });
    return () => node.removeEventListener('wheel', handleWheel);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [items.length]);

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

  return (
    <div
      ref={overlayRef}
      className="game-menu"
      role="dialog"
      aria-modal="true"
      aria-label="Menu điều hướng"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="game-menu-viewport">
        <ul
          ref={trackRef}
          className={`game-menu-track ${ready ? 'is-ready' : ''}`}
          style={{ transform: `translateY(${trackOffset}px)` }}
        >
          {items.map((item, i) => {
            const offset = i - focus;
            const distance = Math.abs(offset);
            const focused = i === focus;

            // Ba tầng rõ rệt: mục đang chọn nổi hẳn lên phía trước và trượt sang phải; hai mục
            // liền kề (distance 1) vẫn sắc nét, chỉ nhỏ hơn; từ distance 2 trở đi rơi vào lớp
            // nền — nhỏ, mờ đục và bị làm nhòe dần.
            const nearby = distance === 1;
            const scale = focused
              ? 'var(--menu-focus-scale, 1.16)'
              : nearby
                ? 0.93
                : Math.max(0.72, 0.86 - (distance - 2) * 0.05);
            const opacity = focused ? 1 : nearby ? 0.88 : Math.max(0.22, 0.55 - (distance - 2) * 0.13);
            const blur = distance <= 1 ? 0 : Math.min(5, 1.6 + (distance - 2) * 1.4);

            // Phần biến đổi 3D nằm ở <li> để CSS còn tự do làm hiệu ứng hover/nhấn trên
            // <button> bên trong — hai transform không giẫm chân nhau.
            const transform = [
              // Độ trượt sang phải và độ phóng của mục đang chọn để trong CSS (--menu-shift,
              // --menu-focus-scale) vì màn hình nhỏ phải trượt/phóng ít hơn cho khỏi tràn.
              focused ? 'translateX(var(--menu-shift, 72px))' : `translateX(${Math.max(0, 26 - distance * 12)}px)`,
              // Z của mục đang chọn giữ ở mức vừa phải: perspective phóng to theo Z, đẩy mạnh
              // quá thì mép phải tràn ra ngoài màn hình dù --menu-room đã chừa chỗ.
              `translateZ(${focused ? 70 : -distance * 46}px)`,
              `rotateY(${focused ? 0 : -17}deg)`,
              `rotateX(${Math.max(-9, Math.min(9, -offset * 1.7))}deg)`,
              `scale(${scale})`,
            ].join(' ');

            return (
              <li
                key={item.key}
                ref={(el) => {
                  slotRefs.current[i] = el;
                }}
                className={`game-menu-slot ${focused ? 'is-focused' : ''} ${blur ? 'is-far' : ''}`}
                style={{
                  transform,
                  opacity,
                  filter: blur ? `blur(${blur}px)` : undefined,
                  zIndex: items.length - distance,
                }}
              >
                <button
                  type="button"
                  className={[
                    'game-menu-item',
                    focused ? 'is-focused' : '',
                    item.danger ? 'is-danger' : '',
                    item.current ? 'is-current' : '',
                  ]
                    .filter(Boolean)
                    .join(' ')}
                  onMouseEnter={() => setFocus(i)}
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
            );
          })}
        </ul>
      </div>

      <div className="game-menu-head">
        <span className="game-menu-logo">
          Wear<span>Wise</span>
        </span>
        <button type="button" className="game-menu-close" onClick={onClose} aria-label="Đóng menu">
          ✕
        </button>
      </div>

      <p className="game-menu-hint">
        🖱️ Cuộn chuột hoặc ↑ ↓ để chọn · Enter để đi · Esc để đóng
      </p>
    </div>
  );
}
