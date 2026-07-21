export const CATEGORY_LABELS = {
  SHIRT: 'Áo',
  PANTS: 'Quần',
  SHOES: 'Giày',
  JACKET: 'Áo khoác',
  ACCESSORY: 'Phụ kiện',
};

export const CATEGORY_EMOJIS = {
  SHIRT: '👕',
  PANTS: '👖',
  SHOES: '👟',
  JACKET: '🧥',
  ACCESSORY: '👜',
};

export const CATEGORY_COLORS = {
  SHIRT: 'var(--blue)',
  PANTS: 'var(--purple)',
  SHOES: 'var(--orange)',
  JACKET: 'var(--green)',
  ACCESSORY: 'var(--pink)',
};

export const SEASON_LABELS = {
  SUMMER: 'Mùa hè',
  WINTER: 'Mùa đông',
  ALL_SEASON: 'Quanh năm',
};

export const SEASON_EMOJIS = {
  SUMMER: '☀️',
  WINTER: '❄️',
  ALL_SEASON: '🍀',
};

export const STYLE_LABELS = {
  CASUAL: 'Thường ngày',
  FORMAL: 'Trang trọng',
  STREETWEAR: 'Đường phố',
  SPORT: 'Thể thao',
};

export const CONDITION_LABELS = {
  GOOD: 'Còn tốt',
  DAMAGED: 'Hư hỏng',
};

export const STATUS_LABELS = {
  AVAILABLE: 'Sẵn sàng',
  LAUNDRY: 'Đang giặt',
  UNAVAILABLE: 'Chưa dùng được',
};

export const STATUS_BADGES = {
  AVAILABLE: 'nb-badge--green',
  LAUNDRY: 'nb-badge--cyan',
  UNAVAILABLE: 'nb-badge--muted',
};

export const SUGGESTION_REASON_LABELS = {
  SEASON_MATCH: '✅ Hợp mùa',
  ALL_SEASON: '🍀 Mặc quanh năm',
  SEASON_MISMATCH: '⚠️ Trái mùa',
  MILD_WEATHER: '🙂 Thời tiết dễ chịu',
  HAS_JACKET: '🧥 Có áo khoác',
  NO_JACKET_IN_RAIN: '☔ Thiếu áo khoác khi mưa',
  JACKET_TOO_WARM: '🥵 Áo khoác hơi nóng',
  FAVORITE: '⭐ Bộ yêu thích',
  NOT_RECENTLY_WORN: '🕐 Lâu rồi chưa mặc',
  ALL_ITEMS_AVAILABLE: '👍 Tất cả món đồ sẵn sàng',
  ITEMS_UNAVAILABLE: '🚫 Có món đồ chưa sẵn sàng',
};

export function label(map, key) {
  return map[key] ?? key ?? '—';
}
