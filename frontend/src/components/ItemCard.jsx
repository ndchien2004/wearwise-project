import { Badge, Button } from './ui';
import {
  CATEGORY_COLORS,
  CATEGORY_EMOJIS,
  CATEGORY_LABELS,
  CONDITION_LABELS,
  SEASON_EMOJIS,
  SEASON_LABELS,
  STATUS_BADGES,
  STATUS_LABELS,
  STYLE_LABELS,
  TONE_BADGE_COLORS,
  TONE_EMOJIS,
  TONE_LABELS,
  label,
} from '../utils/labels';
import { formatDateTime, isWornToday } from '../utils/date';

export default function ItemCard({ item, tried, onOpen, onEdit, onDelete, onToggleFavorite, onWear, onWashed }) {
  const wornToday = isWornToday(item.lastWornAt);
  const inLaundry = item.status === 'LAUNDRY';

  const stop = (handler) => (e) => {
    e.stopPropagation();
    handler(item);
  };

  return (
    <div
      className="nb-card nb-card--hover item-card"
      style={{ cursor: 'pointer' }}
      onClick={() => onOpen(item)}
      title="Bấm để xem chi tiết"
    >
      {item.imageUrl ? (
        <div className="item-photo">
          <img src={item.imageUrl} alt={item.name} />
        </div>
      ) : (
        <div
          className="item-photo item-photo--placeholder"
          style={{ background: CATEGORY_COLORS[item.category] || 'var(--yellow)' }}
        >
          {CATEGORY_EMOJIS[item.category] || '👗'}
        </div>
      )}
      <div className="item-card-top">
        <div style={{ display: 'flex', gap: 12, alignItems: 'center', minWidth: 0 }}>
          <div className="item-emoji-box" style={{ background: CATEGORY_COLORS[item.category] || 'var(--yellow)' }}>
            {CATEGORY_EMOJIS[item.category] || '👗'}
          </div>
          <div style={{ minWidth: 0 }}>
            <div className="item-name" title={item.name}>{item.name}</div>
            <div className="item-meta">
              {item.color ? `Màu: ${item.color} · ` : ''}
              Đã mặc {item.wearCount ?? 0} lần
            </div>
          </div>
        </div>
        <button
          type="button"
          className={`fav-btn ${item.favorite ? 'is-fav' : ''}`}
          onClick={stop(onToggleFavorite)}
          title={item.favorite ? 'Bỏ yêu thích' : 'Yêu thích'}
        >
          ⭐
        </button>
      </div>

      <div className="badge-row">
        <Badge color="yellow">{label(CATEGORY_LABELS, item.category)}</Badge>
        <Badge color="blue">
          {SEASON_EMOJIS[item.season]} {label(SEASON_LABELS, item.season)}
        </Badge>
        <Badge color="purple">{label(STYLE_LABELS, item.style)}</Badge>
        {item.colorTone && (
          <Badge color={TONE_BADGE_COLORS[item.colorTone]}>
            {TONE_EMOJIS[item.colorTone]} {label(TONE_LABELS, item.colorTone)}
          </Badge>
        )}
        <Badge color={item.condition === 'DAMAGED' ? 'red' : undefined}>
          {label(CONDITION_LABELS, item.condition)}
        </Badge>
        <span className={`nb-badge ${STATUS_BADGES[item.status] || ''}`}>
          {label(STATUS_LABELS, item.status)}
        </span>
        {tried && <Badge color="pink">🪞 Đã thử</Badge>}
      </div>

      <div className="item-meta">Mặc lần cuối: {formatDateTime(item.lastWornAt)}</div>

      <div className="card-actions">
        {inLaundry ? (
          <Button size="sm" variant="green" onClick={stop(onWashed)}>
            ✅ Giặt xong
          </Button>
        ) : wornToday ? (
          <Button size="sm" disabled onClick={(e) => e.stopPropagation()}>
            ✅ Đã mặc
          </Button>
        ) : (
          <Button size="sm" variant="green" onClick={stop(onWear)}>
            👣 Mặc
          </Button>
        )}
        <Button size="sm" onClick={stop(onEdit)}>
          ✏️ Sửa
        </Button>
        <Button size="sm" variant="danger" onClick={stop(onDelete)}>
          🗑️
        </Button>
      </div>
    </div>
  );
}
