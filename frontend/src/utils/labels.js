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

export const TONE_LABELS = {
  WARM: 'Tông ấm',
  COOL: 'Tông lạnh',
  NEUTRAL: 'Trung tính',
  PASTEL: 'Pastel',
  BRIGHT: 'Rực rỡ',
  DARK: 'Tông tối',
};

export const TONE_EMOJIS = {
  WARM: '🔥',
  COOL: '🧊',
  NEUTRAL: '🤍',
  PASTEL: '🌸',
  BRIGHT: '🌈',
  DARK: '🖤',
};

// Màu badge cho từng tone (dùng với <Badge color=...>).
export const TONE_BADGE_COLORS = {
  WARM: 'orange',
  COOL: 'cyan',
  NEUTRAL: undefined,
  PASTEL: 'pink',
  BRIGHT: 'yellow',
  DARK: 'purple',
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
};

/**
 * Những lý do là điểm trừ, tô đỏ trên thẻ gợi ý. Liệt kê thẳng tên hằng số thay vì đoán theo chuỗi
 * con: cách cũ dò `includes('UNAVAILABLE')` nên "🥵 Áo khoác hơi nóng" — một điểm trừ thật — lại
 * được tô xanh, còn nhánh `UNAVAILABLE` thì không bao giờ tới vì gợi ý đã lọc bộ mặc được.
 */
export const NEGATIVE_SUGGESTION_REASONS = new Set([
  'SEASON_MISMATCH',
  'NO_JACKET_IN_RAIN',
  'JACKET_TOO_WARM',
]);

/**
 * Loại sự kiện trong nhật ký kiểm toán. Thứ tự khai báo cũng là thứ tự hiển thị trong dropdown
 * lọc và biểu đồ: nhóm truy cập trước, rồi mật khẩu, rồi cảnh báo, cuối cùng là hành động
 * của quản trị viên.
 */
export const AUDIT_ACTION_LABELS = {
  LOGIN_SUCCEEDED: 'Đăng nhập thành công',
  LOGIN_FAILED: 'Đăng nhập thất bại',
  ACCOUNT_AUTO_LOCKED: 'Tự động khóa tài khoản',
  LOGGED_OUT: 'Đăng xuất',
  ACCOUNT_REGISTERED: 'Tạo tài khoản',
  PASSWORD_CHANGED: 'Đổi mật khẩu',
  PASSWORD_RESET_REQUESTED: 'Yêu cầu đặt lại mật khẩu',
  PASSWORD_RESET_COMPLETED: 'Đã đặt lại mật khẩu',
  REFRESH_TOKEN_REUSE_DETECTED: 'Phát hiện token bị dùng lại',
  ADMIN_LOCKED_ACCOUNT: 'Quản trị viên khóa tài khoản',
  ADMIN_UNLOCKED_ACCOUNT: 'Quản trị viên mở khóa',
  ADMIN_CHANGED_RATE_LIMIT: 'Quản trị viên đổi hạn mức',
};

export const AUDIT_ACTION_EMOJIS = {
  LOGIN_SUCCEEDED: '✅',
  LOGIN_FAILED: '🚫',
  ACCOUNT_AUTO_LOCKED: '🔒',
  LOGGED_OUT: '👋',
  ACCOUNT_REGISTERED: '🎉',
  PASSWORD_CHANGED: '🔑',
  PASSWORD_RESET_REQUESTED: '📧',
  PASSWORD_RESET_COMPLETED: '🔓',
  REFRESH_TOKEN_REUSE_DETECTED: '🚨',
  ADMIN_LOCKED_ACCOUNT: '🛡️',
  ADMIN_UNLOCKED_ACCOUNT: '🛡️',
  ADMIN_CHANGED_RATE_LIMIT: '🎚️',
};

/**
 * Màu badge theo mức độ đáng chú ý, không phải theo loại: sự kiện cần người xem xét thì đỏ,
 * hành động quản trị thì tím (để phân biệt với hoạt động của người dùng thường), còn lại trung tính.
 */
export const AUDIT_ACTION_BADGES = {
  LOGIN_SUCCEEDED: 'nb-badge--green',
  LOGIN_FAILED: 'nb-badge--orange',
  ACCOUNT_AUTO_LOCKED: 'nb-badge--red',
  LOGGED_OUT: 'nb-badge--muted',
  ACCOUNT_REGISTERED: 'nb-badge--cyan',
  PASSWORD_CHANGED: 'nb-badge--yellow',
  PASSWORD_RESET_REQUESTED: 'nb-badge--yellow',
  PASSWORD_RESET_COMPLETED: 'nb-badge--yellow',
  REFRESH_TOKEN_REUSE_DETECTED: 'nb-badge--red',
  ADMIN_LOCKED_ACCOUNT: 'nb-badge--purple',
  ADMIN_UNLOCKED_ACCOUNT: 'nb-badge--purple',
  ADMIN_CHANGED_RATE_LIMIT: 'nb-badge--purple',
};

/** Vì sao một món chưa mặc được — khớp enum ItemBlockReason ở backend. */
export const BLOCK_REASON_LABELS = {
  ARCHIVED: 'đã ẩn khỏi tủ',
  LAUNDRY: 'đang giặt',
  UNAVAILABLE: 'chưa dùng được',
  DAMAGED: 'đang hư hỏng',
};

export const BLOCK_REASON_EMOJIS = {
  ARCHIVED: '📦',
  LAUNDRY: '🧺',
  UNAVAILABLE: '🚫',
  DAMAGED: '🩹',
};

/**
 * Câu tóm tắt vì sao bộ chưa mặc được, gom các món cùng lý do lại: "🧺 Áo sơ mi, Quần jean đang
 * giặt" thay vì lặp cụm "đang giặt" sau từng tên món.
 */
export function describeBlockers(blockingItems) {
  if (!blockingItems?.length) return '';

  const byReason = new Map();
  blockingItems.forEach(({ itemName, reason }) => {
    if (!byReason.has(reason)) byReason.set(reason, []);
    byReason.get(reason).push(itemName);
  });

  return [...byReason.entries()]
    .map(([reason, names]) => `${BLOCK_REASON_EMOJIS[reason] ?? '⚠️'} ${names.join(', ')} ${label(BLOCK_REASON_LABELS, reason)}`)
    .join(' · ');
}

export function label(map, key) {
  return map[key] ?? key ?? '—';
}
