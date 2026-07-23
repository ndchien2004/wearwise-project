import { CATEGORY_COLORS, CATEGORY_EMOJIS } from '../utils/labels';

/**
 * Khối hình ảnh trực quan cho một outfit: ảnh đại diện (nếu có) hoặc collage các món,
 * kèm dải thumbnail của từng món áo/quần tạo nên bộ.
 */
export default function OutfitVisual({ outfit }) {
  const items = outfit.clothingItems ?? [];
  const collage = items.filter((item) => item.imageUrl).slice(0, 4);

  return (
    <>
      {outfit.imageUrl ? (
        <div className="item-photo">
          <img src={outfit.imageUrl} alt={outfit.name} />
        </div>
      ) : collage.length > 0 ? (
        <div
          className="outfit-collage"
          style={collage.length === 1 ? { gridTemplateColumns: '1fr' } : undefined}
        >
          {collage.map((item) => (
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

      {items.length > 0 && (
        <div className="outfit-thumb-row">
          {items.map((item) => (
            <div key={item.id} className="outfit-thumb" title={item.name}>
              {item.imageUrl ? (
                <img src={item.imageUrl} alt={item.name} />
              ) : (
                <span
                  className="outfit-thumb--emoji"
                  style={{ background: CATEGORY_COLORS[item.category] || 'var(--yellow)' }}
                >
                  {CATEGORY_EMOJIS[item.category] || '👗'}
                </span>
              )}
            </div>
          ))}
        </div>
      )}
    </>
  );
}
