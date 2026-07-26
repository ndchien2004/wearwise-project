import { useNavigate } from 'react-router-dom';
import HoverCard from './HoverCard';
import { CATEGORY_COLORS, CATEGORY_EMOJIS, SEASON_LABELS, STYLE_LABELS, label } from '../utils/labels';
import { formatDateTime } from '../utils/date';

function Thumb({ item }) {
  return (
    <span className="plan-preview-thumb" title={item.name}>
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
  );
}

/**
 * Thẻ xem nhanh một outfit: ảnh bộ (hoặc ghép ảnh các món), tên, thông tin phụ và nút mở chi tiết.
 * Dùng chung cho chip trong lịch và các bảng thống kê ở trang chủ.
 */
export function OutfitPreviewChip({
  outfit,
  meta,
  note,
  children,
  className = 'wear-chip',
  activeClassName = 'is-previewing',
}) {
  const navigate = useNavigate();
  const items = outfit.clothingItems ?? [];
  const collage = items.filter((item) => item.imageUrl).slice(0, 4);

  const card = (
    <>
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
        {meta ? `${meta} · ` : ''}
        {items.length} món
      </div>

      {note && <div className="plan-preview-note">📝 {note}</div>}

      {items.length > 0 && (
        <div className="plan-preview-thumbs">
          {items.slice(0, 6).map((item) => (
            <Thumb key={item.id} item={item} />
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
    </>
  );

  return (
    <HoverCard card={card} className={className} activeClassName={activeClassName} title={outfit.name}>
      {children ?? outfit.name}
    </HoverCard>
  );
}

/** Thẻ xem nhanh một món đồ. */
export function ItemPreviewChip({ item, meta, children, className = 'wear-chip' }) {
  const navigate = useNavigate();

  const card = (
    <>
      {item.imageUrl ? (
        <div className="plan-preview-media">
          <img src={item.imageUrl} alt={item.name} />
        </div>
      ) : (
        <div
          className="plan-preview-media plan-preview-media--empty"
          style={{ background: CATEGORY_COLORS[item.category] || 'var(--yellow)' }}
        >
          {CATEGORY_EMOJIS[item.category] || '👗'}
        </div>
      )}

      <div className="plan-preview-name">
        {CATEGORY_EMOJIS[item.category]} {item.name}
      </div>
      <div className="plan-preview-meta">
        {meta ? `${meta} · ` : ''}
        {label(SEASON_LABELS, item.season)} · {label(STYLE_LABELS, item.style)}
      </div>
      <div className="plan-preview-meta">
        👣 {item.wearCount ?? 0} lượt · 🕐 {formatDateTime(item.lastWornAt)}
      </div>

      <button
        type="button"
        className="nb-btn nb-btn--sm nb-btn--primary plan-preview-cta"
        onClick={() => navigate(`/wardrobe/${item.id}`)}
      >
        🔍 Xem chi tiết
      </button>
    </>
  );

  return (
    <HoverCard card={card} className={className} title={item.name}>
      {children ?? (
        <>
          {CATEGORY_EMOJIS[item.category]} {item.name}
        </>
      )}
    </HoverCard>
  );
}
