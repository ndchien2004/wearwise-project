import { useState } from 'react';
import { Badge, Button } from './ui';
import { isWornToday } from '../utils/date';

export default function OutfitCard({ outfit, tried, onOpen, onEdit, onDelete, onToggleFavorite, onWear }) {
  const collageImages = outfit.clothingItems.filter((item) => item.imageUrl).slice(0, 4);
  const wornToday = isWornToday(outfit.lastWornAt);
  const [flipped, setFlipped] = useState(false);

  // Ảnh lật được khi có cả ảnh đại diện (mặt trước) lẫn ảnh các món (mặt sau).
  const flippable = Boolean(outfit.imageUrl) && collageImages.length > 0;

  const stop = (handler) => (e) => {
    e.stopPropagation();
    handler(outfit);
  };

  const handleFlip = (e) => {
    e.stopPropagation();
    setFlipped((v) => !v);
  };

  const renderCollage = () => (
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
  );

  return (
    <div
      className="nb-card nb-card--hover item-card"
      style={{ cursor: 'pointer' }}
      onClick={() => onOpen(outfit)}
      title="Bấm để xem chi tiết"
    >
      {flippable ? (
        <div
          className={`outfit-flip ${flipped ? 'is-flipped' : ''}`}
          onClick={handleFlip}
          title={flipped ? 'Bấm để xem lại ảnh bộ' : 'Bấm để xem các món'}
        >
          <div className="outfit-flip-inner">
            <div className="outfit-flip-face outfit-flip-front">
              <img src={outfit.imageUrl} alt={outfit.name} />
              {tried && <span className="outfit-badge-overlay">🪞 Đã thử</span>}
              <div className="outfit-eye" aria-hidden="true">👁️</div>
            </div>
            <div className="outfit-flip-face outfit-flip-back">
              {renderCollage()}
            </div>
          </div>
        </div>
      ) : outfit.imageUrl ? (
        <div className="item-photo" style={{ position: 'relative' }}>
          <img src={outfit.imageUrl} alt={outfit.name} />
          {tried && <span className="outfit-badge-overlay">🪞 Đã thử</span>}
        </div>
      ) : collageImages.length > 0 ? (
        <div style={{ position: 'relative' }}>
          {renderCollage()}
          {tried && <span className="outfit-badge-overlay">🪞 Đã thử</span>}
        </div>
      ) : (
        <div className="item-photo item-photo--placeholder" style={{ background: 'var(--pink)', position: 'relative' }}>
          🧢
          {tried && <span className="outfit-badge-overlay">🪞 Đã thử</span>}
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

      <div className="card-actions">
        {wornToday ? (
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
