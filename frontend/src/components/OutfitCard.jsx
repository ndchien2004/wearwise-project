import { Badge, Button } from './ui';
import {
  CATEGORY_EMOJIS,
  SEASON_EMOJIS,
  SEASON_LABELS,
  STYLE_LABELS,
  label,
} from '../utils/labels';
import { formatDateTime, isWornToday } from '../utils/date';

export default function OutfitCard({ outfit, onEdit, onDelete, onToggleFavorite, onWear }) {
  const collageImages = outfit.clothingItems.filter((item) => item.imageUrl).slice(0, 4);
  const wornToday = isWornToday(outfit.lastWornAt);

  return (
    <div className="nb-card nb-card--hover item-card">
      {collageImages.length > 0 && (
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
      )}
      <div className="item-card-top">
        <div style={{ minWidth: 0 }}>
          <div className="item-name">🧢 {outfit.name}</div>
          {outfit.description && (
            <div className="item-meta" style={{ marginTop: 4 }}>
              {outfit.description}
            </div>
          )}
        </div>
        <button
          type="button"
          className={`fav-btn ${outfit.favorite ? 'is-fav' : ''}`}
          onClick={() => onToggleFavorite(outfit)}
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
          <Button size="sm" disabled>
            ✅ Đã mặc hôm nay
          </Button>
        ) : (
          <Button size="sm" variant="green" onClick={() => onWear(outfit)}>
            👣 Mặc hôm nay
          </Button>
        )}
        <Button size="sm" onClick={() => onEdit(outfit)}>
          ✏️ Sửa
        </Button>
        <Button size="sm" variant="danger" onClick={() => onDelete(outfit)}>
          🗑️
        </Button>
      </div>
    </div>
  );
}
