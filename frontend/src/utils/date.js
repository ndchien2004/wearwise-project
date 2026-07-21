export function toIsoDate(date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

export function todayIso() {
  return toIsoDate(new Date());
}

export function formatDate(isoDate) {
  if (!isoDate) return '—';
  const [y, m, d] = isoDate.split('-');
  return `${d}/${m}/${y}`;
}

export function formatDateTime(isoDateTime) {
  if (!isoDateTime) return 'Chưa bao giờ';
  const date = new Date(isoDateTime);
  if (Number.isNaN(date.getTime())) return '—';
  return date.toLocaleDateString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
}

export function isWornToday(isoDateTime) {
  if (!isoDateTime) return false;
  const date = new Date(isoDateTime);
  if (Number.isNaN(date.getTime())) return false;
  return toIsoDate(date) === todayIso();
}

export function daysSince(isoDateTime) {
  if (!isoDateTime) return null;
  const date = new Date(isoDateTime);
  if (Number.isNaN(date.getTime())) return null;
  return Math.floor((Date.now() - date.getTime()) / (1000 * 60 * 60 * 24));
}

export const MONTH_NAMES = [
  'Tháng 1', 'Tháng 2', 'Tháng 3', 'Tháng 4', 'Tháng 5', 'Tháng 6',
  'Tháng 7', 'Tháng 8', 'Tháng 9', 'Tháng 10', 'Tháng 11', 'Tháng 12',
];

export const DOW_NAMES = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];

// Trả về mảng các ngày hiển thị trong lưới lịch tháng (bắt đầu từ Thứ 2).
export function buildMonthGrid(year, month) {
  const firstDay = new Date(year, month, 1);
  // getDay(): 0=CN..6=T7 → đổi sang 0=T2..6=CN
  const leadingDays = (firstDay.getDay() + 6) % 7;
  const start = new Date(year, month, 1 - leadingDays);

  const cells = [];
  for (let i = 0; i < 42; i++) {
    const date = new Date(start.getFullYear(), start.getMonth(), start.getDate() + i);
    cells.push({
      date,
      iso: toIsoDate(date),
      inMonth: date.getMonth() === month,
    });
  }
  return cells;
}
