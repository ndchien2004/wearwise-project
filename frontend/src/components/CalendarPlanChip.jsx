import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useNavigate } from 'react-router-dom';
import { CATEGORY_COLORS, CATEGORY_EMOJIS } from '../utils/labels';

const CARD_WIDTH = 240;
const GAP = 10;

/**
 * Chip tên outfit trong ô ngày của lịch. Hover (hoặc focus) sẽ mở một thẻ xem
 * nhanh: ảnh bộ đồ, các món trong bộ và nút mở trang chi tiết outfit.
 *
 * Thẻ được render qua portal vì ô ngày có `overflow: hidden` sẽ cắt mất popup.
 */
export default function CalendarPlanChip({ plan }) {
  const navigate = useNavigate();
  const chipRef = useRef(null);
  const cardRef = useRef(null);
  const closeTimer = useRef(null);
  const [pos, setPos] = useState(null);

  const outfit = plan.outfit;
  const items = outfit.clothingItems ?? [];
  const collage = items.filter((item) => item.imageUrl).slice(0, 4);

  const cancelClose = useCallback(() => {
    clearTimeout(closeTimer.current);
  }, []);

  const open = useCallback(() => {
    cancelClose();
    const rect = chipRef.current?.getBoundingClientRect();
    if (!rect) return;
    // Mặc định hiện bên phải chip; sát mép phải màn hình thì lật sang trái.
    const right = rect.right + GAP;
    const left = right + CARD_WIDTH > window.innerWidth - GAP ? rect.left - CARD_WIDTH - GAP : right;
    setPos({ top: rect.top, left: Math.max(GAP, left) });
  }, [cancelClose]);

  // Trễ một nhịp để con trỏ kịp đi từ chip sang thẻ mà thẻ không đóng.
  const scheduleClose = useCallback(() => {
    cancelClose();
    closeTimer.current = setTimeout(() => setPos(null), 150);
  }, [cancelClose]);

  useEffect(() => cancelClose, [cancelClose]);

  // Toạ độ là `fixed` nên cuộn trang sẽ làm thẻ lệch khỏi chip — đóng luôn.
  useEffect(() => {
    if (!pos) return undefined;
    const close = () => setPos(null);
    window.addEventListener('scroll', close, true);
    window.addEventListener('resize', close);
    return () => {
      window.removeEventListener('scroll', close, true);
      window.removeEventListener('resize', close);
    };
  }, [pos]);

  // Kéo thẻ lên khi nó tràn khỏi đáy màn hình (chiều cao chỉ biết sau khi render).
  useLayoutEffect(() => {
    if (!pos || !cardRef.current) return;
    const height = cardRef.current.offsetHeight;
    const maxTop = window.innerHeight - height - GAP;
    const nextTop = Math.max(GAP, Math.min(pos.top, maxTop));
    if (Math.abs(nextTop - pos.top) > 1) {
      setPos((prev) => (prev ? { ...prev, top: nextTop } : prev));
    }
  }, [pos]);

  return (
    <>
      <span
        ref={chipRef}
        className={`calendar-plan-chip ${plan.completed ? 'is-done' : ''} ${pos ? 'is-previewing' : ''}`}
        title={outfit.name}
        tabIndex={0}
        onMouseEnter={open}
        onMouseLeave={scheduleClose}
        onFocus={open}
        onBlur={scheduleClose}
      >
        {outfit.name}
      </span>

      {pos &&
        createPortal(
          <div
            ref={cardRef}
            className="plan-preview"
            style={{ top: pos.top, left: pos.left, width: CARD_WIDTH }}
            onMouseEnter={cancelClose}
            onMouseLeave={scheduleClose}
            // Portal vẫn nổi bọt sự kiện theo cây React nên phải chặn,
            // không thì bấm vào thẻ sẽ mở luôn modal của ô ngày.
            onClick={(e) => e.stopPropagation()}
          >
            {outfit.imageUrl ? (
              <div className="plan-preview-media">
                <img src={outfit.imageUrl} alt={outfit.name} />
              </div>
            ) : collage.length > 0 ? (
              <div
                className="plan-preview-media plan-preview-collage"
                style={collage.length === 1 ? { gridTemplateColumns: '1fr' } : undefined}
              >
                {collage.map((item) => (
                  <img key={item.id} src={item.imageUrl} alt={item.name} />
                ))}
              </div>
            ) : (
              <div className="plan-preview-media plan-preview-media--empty">🧢</div>
            )}

            <div className="plan-preview-name">🧢 {outfit.name}</div>
            <div className="plan-preview-meta">
              {plan.completed ? '✅ Đã mặc' : '🕐 Chưa mặc'} · {items.length} món
            </div>

            {plan.note && <div className="plan-preview-note">📝 {plan.note}</div>}

            {items.length > 0 && (
              <div className="plan-preview-thumbs">
                {items.slice(0, 6).map((item) => (
                  <span key={item.id} className="plan-preview-thumb" title={item.name}>
                    {item.imageUrl ? (
                      <img src={item.imageUrl} alt={item.name} />
                    ) : (
                      <span
                        className="plan-preview-thumb-emoji"
                        style={{ background: CATEGORY_COLORS[item.category] || 'var(--yellow)' }}
                      >
                        {CATEGORY_EMOJIS[item.category] || '👗'}
                      </span>
                    )}
                  </span>
                ))}
                {items.length > 6 && <span className="plan-preview-more">+{items.length - 6}</span>}
              </div>
            )}

            <button
              type="button"
              className="nb-btn nb-btn--sm nb-btn--primary plan-preview-cta"
              onClick={() => navigate(`/outfits/${outfit.id}`)}
            >
              🔍 Xem chi tiết
            </button>
          </div>,
          document.body
        )}
    </>
  );
}
