import { useEffect, useRef, useState } from 'react';

const MIN_ZOOM = 1;
const MAX_ZOOM = 5;
const ZOOM_STEP = 0.4;

/**
 * Xem ảnh phóng to: cuộn chuột để zoom, kéo để di chuyển khi đã phóng, Esc để đóng.
 *
 * Ảnh thử đồ chỉ có ý nghĩa khi soi được chi tiết — đường may, cổ áo, chất vải — mà khung ảnh
 * trong trang thì nhỏ. Mở tab mới xem ảnh gốc cũng được nhưng người dùng mất ngữ cảnh so sánh.
 */
export default function ImageLightbox({ src, alt, onClose }) {
  const [zoom, setZoom] = useState(1);
  const [offset, setOffset] = useState({ x: 0, y: 0 });
  const dragState = useRef(null);

  useEffect(() => {
    const onKey = (event) => {
      if (event.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);

    // Khoá cuộn nền: cuộn chuột trong lightbox là để zoom, không phải để trang chạy phía sau.
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = previousOverflow;
    };
  }, [onClose]);

  const clampZoom = (value) => Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, value));

  const changeZoom = (delta) => {
    setZoom((current) => {
      const next = clampZoom(current + delta);
      // Về đúng tỉ lệ gốc thì đưa ảnh về giữa, tránh ảnh "kẹt" lệch ngoài khung.
      if (next === MIN_ZOOM) setOffset({ x: 0, y: 0 });
      return next;
    });
  };

  const handleWheel = (event) => {
    changeZoom(event.deltaY < 0 ? ZOOM_STEP : -ZOOM_STEP);
  };

  const handlePointerDown = (event) => {
    if (zoom === MIN_ZOOM) return;
    dragState.current = { startX: event.clientX, startY: event.clientY, origin: offset };
    event.currentTarget.setPointerCapture(event.pointerId);
  };

  const handlePointerMove = (event) => {
    const drag = dragState.current;
    if (!drag) return;
    setOffset({
      x: drag.origin.x + (event.clientX - drag.startX),
      y: drag.origin.y + (event.clientY - drag.startY),
    });
  };

  const handlePointerUp = () => {
    dragState.current = null;
  };

  return (
    <div
      className="lightbox-overlay"
      onMouseDown={(event) => {
        // Chỉ đóng khi bấm ra nền, không đóng khi vừa kéo ảnh xong.
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <div className="lightbox-toolbar">
        <button type="button" className="nb-btn nb-btn--sm" onClick={() => changeZoom(-ZOOM_STEP)} aria-label="Thu nhỏ">
          −
        </button>
        <span className="lightbox-zoom-value">{Math.round(zoom * 100)}%</span>
        <button type="button" className="nb-btn nb-btn--sm" onClick={() => changeZoom(ZOOM_STEP)} aria-label="Phóng to">
          +
        </button>
        <button
          type="button"
          className="nb-btn nb-btn--sm"
          onClick={() => {
            setZoom(1);
            setOffset({ x: 0, y: 0 });
          }}
        >
          Vừa khung
        </button>
        <a className="nb-btn nb-btn--sm" href={src} target="_blank" rel="noreferrer">
          ↗ Ảnh gốc
        </a>
        <button type="button" className="nb-btn nb-btn--sm nb-btn--dark" onClick={onClose} aria-label="Đóng">
          ✕
        </button>
      </div>

      <div className="lightbox-stage" onWheel={handleWheel}>
        <img
          className="lightbox-img"
          src={src}
          alt={alt}
          draggable={false}
          style={{
            transform: `translate(${offset.x}px, ${offset.y}px) scale(${zoom})`,
            cursor: zoom > MIN_ZOOM ? 'grab' : 'zoom-in',
          }}
          onPointerDown={handlePointerDown}
          onPointerMove={handlePointerMove}
          onPointerUp={handlePointerUp}
          onPointerCancel={handlePointerUp}
          onClick={() => {
            if (zoom === MIN_ZOOM) changeZoom(ZOOM_STEP * 2);
          }}
        />
      </div>

      <p className="lightbox-hint">Cuộn chuột để phóng to · Kéo để di chuyển · Esc để đóng</p>
    </div>
  );
}
