import { useCallback, useEffect, useRef, useState } from 'react';

const VIEWPORT = 300; // cạnh khung xem trước (px)
const OUTPUT = 512; // cạnh ảnh xuất ra (px vuông, sẽ được bo tròn khi hiển thị)
const MAX_ZOOM = 3;

/**
 * Cho phép người dùng kéo/phóng để căn ảnh vào khung tròn của avatar trước khi tải lên.
 * Xuất ra một ảnh vuông (JPEG) đã cắt đúng vùng nằm trong khung — phần hiển thị bo tròn.
 */
export default function AvatarCropModal({ file, onCancel, onConfirm }) {
  const [img, setImg] = useState(null);
  const [zoom, setZoom] = useState(1);
  const [offset, setOffset] = useState({ x: 0, y: 0 });
  const [saving, setSaving] = useState(false);
  const dragStart = useRef(null);
  const stageRef = useRef(null);

  // Nạp ảnh để biết kích thước thật; thu hồi object URL khi đóng.
  useEffect(() => {
    const url = URL.createObjectURL(file);
    const image = new Image();
    image.onload = () => setImg(image);
    image.src = url;
    return () => URL.revokeObjectURL(url);
  }, [file]);

  // Phóng để cạnh ngắn phủ kín khung (cover), người dùng chỉ phóng to thêm từ mức này.
  const baseScale = img ? VIEWPORT / Math.min(img.width, img.height) : 1;
  const scale = baseScale * zoom;
  const renderW = img ? img.width * scale : 0;
  const renderH = img ? img.height * scale : 0;

  // Giữ ảnh luôn phủ kín khung: giới hạn phần lệch tối đa theo mỗi trục.
  const clampOffset = useCallback(
    (next) => {
      const maxX = Math.max(0, (renderW - VIEWPORT) / 2);
      const maxY = Math.max(0, (renderH - VIEWPORT) / 2);
      return {
        x: Math.min(maxX, Math.max(-maxX, next.x)),
        y: Math.min(maxY, Math.max(-maxY, next.y)),
      };
    },
    [renderW, renderH]
  );

  useEffect(() => {
    setOffset((o) => clampOffset(o));
  }, [clampOffset]);

  useEffect(() => {
    const onKey = (e) => {
      if (e.key === 'Escape') onCancel();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onCancel]);

  const onPointerDown = (e) => {
    e.currentTarget.setPointerCapture(e.pointerId);
    dragStart.current = { px: e.clientX, py: e.clientY, ...offset };
  };

  const onPointerMove = (e) => {
    if (!dragStart.current) return;
    const { px, py, x, y } = dragStart.current;
    setOffset(clampOffset({ x: x + (e.clientX - px), y: y + (e.clientY - py) }));
  };

  const onPointerUp = () => {
    dragStart.current = null;
  };

  // React gắn onWheel ở chế độ passive nên preventDefault trong đó không ăn — tự gắn listener
  // non-passive để cuộn chuột phóng ảnh mà không cuộn trang.
  useEffect(() => {
    const node = stageRef.current;
    if (!node) return undefined;
    const handleWheel = (e) => {
      e.preventDefault();
      setZoom((z) => Math.min(MAX_ZOOM, Math.max(1, z - e.deltaY * 0.0015)));
    };
    node.addEventListener('wheel', handleWheel, { passive: false });
    return () => node.removeEventListener('wheel', handleWheel);
  }, []);

  const handleConfirm = () => {
    if (!img) return;
    const imgLeft = (VIEWPORT - renderW) / 2 + offset.x;
    const imgTop = (VIEWPORT - renderH) / 2 + offset.y;
    const sourceSize = VIEWPORT / scale;
    const sx = Math.max(0, Math.min(img.width - sourceSize, -imgLeft / scale));
    const sy = Math.max(0, Math.min(img.height - sourceSize, -imgTop / scale));

    const canvas = document.createElement('canvas');
    canvas.width = OUTPUT;
    canvas.height = OUTPUT;
    const ctx = canvas.getContext('2d');
    ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(img, sx, sy, sourceSize, sourceSize, 0, 0, OUTPUT, OUTPUT);

    setSaving(true);
    canvas.toBlob(
      (blob) => {
        if (blob) onConfirm(blob);
        else setSaving(false);
      },
      'image/jpeg',
      0.92
    );
  };

  const imgLeft = (VIEWPORT - renderW) / 2 + offset.x;
  const imgTop = (VIEWPORT - renderH) / 2 + offset.y;

  return (
    <div
      className="nb-modal-overlay"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onCancel();
      }}
    >
      <div className="nb-modal avatar-crop-modal">
        <h3 className="nb-modal-title" style={{ textAlign: 'center' }}>Căn chỉnh ảnh đại diện</h3>
        <p className="avatar-crop-hint">Kéo để di chuyển, cuộn chuột hoặc dùng thanh trượt để phóng to.</p>

        <div
          ref={stageRef}
          className="avatar-crop-stage"
          style={{ width: VIEWPORT, height: VIEWPORT }}
          onPointerDown={onPointerDown}
          onPointerMove={onPointerMove}
          onPointerUp={onPointerUp}
          onPointerCancel={onPointerUp}
        >
          {img && (
            <img
              className="avatar-crop-img"
              src={img.src}
              alt=""
              draggable="false"
              style={{
                width: renderW,
                height: renderH,
                transform: `translate(${imgLeft}px, ${imgTop}px)`,
              }}
            />
          )}
          <div className="avatar-crop-mask" />
        </div>

        <input
          className="avatar-crop-zoom"
          type="range"
          min="1"
          max={MAX_ZOOM}
          step="0.01"
          value={zoom}
          onChange={(e) => setZoom(Number(e.target.value))}
          aria-label="Phóng to"
        />

        <div className="confirm-actions">
          <button type="button" className="nb-btn" onClick={onCancel} disabled={saving}>
            Hủy
          </button>
          <button
            type="button"
            className="nb-btn nb-btn--primary"
            onClick={handleConfirm}
            disabled={!img || saving}
          >
            {saving ? 'Đang lưu...' : 'Dùng ảnh này'}
          </button>
        </div>
      </div>
    </div>
  );
}
