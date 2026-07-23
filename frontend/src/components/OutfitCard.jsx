import { Badge, Button } from './ui';
import {
  CATEGORY_EMOJIS,
  SEASON_EMOJIS,
  SEASON_LABELS,
  STYLE_LABELS,
  label,
} from '../utils/labels';
import { formatDateTime, isWornToday } from '../utils/date';

export default function OutfitCard({ outfit, tried, onOpen, onEdit, onDelete, onToggleFavorite, onWear }) {
  const collageImages = outfit.clothingItems.filter((item) => item.imageUrl).slice(0, 4);
  const wornToday = isWornToday(outfit.lastWornAt);

  const stop = (handler) => (e) => {
    e.stopPropagation();
    handler(outfit);
  };

  return (
    <div
      className="nb-card nb-card--hover item-card"
      style={{ cursor: 'pointer' }}
      onClick={() => onOpen(outfit)}
      title="Bấm để xem chi tiết"
    >
      {collageImages.length > 0 ? (
        <div
          className="outfit-collage"
          style={collageImages.length === 1 ? { gridTemplateColumns: '1fr' } : undefined}
        >
          {collageImages.map((item) => (
            <div key={item.id} className="collage-cell" title={item.name}>
              <img src={item.imageUrl} alt={item.name} />
            </div>
          ))}
        </div>
      ) : (
        <div className="item-photo item-photo--placeholder" style={{ background: 'var(--pink)' }}>
          🧢
        </div>
      )}
      <div className="item-card-top">
        <div style={{ minWidth: 0 }}>
          <div className="item-name" title={outfit.name}>🧢 {outfit.name}</div>
          <div className="item-meta">
            {outfit.description || `${outfit.clothingItems.length} món đồ`}
          </div>
        </div>
        <button
          type="button"
          className={`fav-btn ${outfit.favorite ? 'is-fav' : ''}`}
          onClick={stop(onToggleFavorite)}
          title={outfit.favorite ? 'Bỏ yêu thích' : 'Yêu thích'}
        >
          ⭐
        </button>
      </div>

      <div className="badge-row">
        <Badge color="blue">
          {SEASON_EMOJIS[outfit.season]} {label(SEASON_LABELS, outfit.season)}
        </Badge>
        <Badge color="purple">{label(STYLE_LABELS, outfit.style)}</Badge>
        <Badge color="orange">Đã mặc {outfit.wearCount ?? 0} lần</Badge>
        {tried && <Badge color="pink">🪞 Đã thử</Badge>}
      </div>

      <div className="badge-row">
        {outfit.clothingItems.map((item) => (
          <Badge key={item.id}>
            {CATEGORY_EMOJIS[item.category]} {item.name}
          </Badge>
        ))}
      </div>

      <div className="item-meta">Mặc lần cuối: {formatDateTime(outfit.lastWornAt)}</div>

      <div className="card-actions">
        {wornToday ? (
          <Button size="sm" disabled onClick={(e) => e.stopPropagation()}>
            ✅ Đã mặc hôm nay
          </Button>
        ) : (
          <Button size="sm" variant="green" onClick={stop(onWear)}>
            👣 Mặc hôm nay
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
