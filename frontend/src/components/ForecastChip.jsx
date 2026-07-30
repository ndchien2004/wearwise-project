/**
 * Một ngày trong dải dự báo ở trang Gợi ý.
 *
 * Gọn theo chiều dọc là yêu cầu chính: cả dải phải nằm cùng khung với thời tiết hiện tại, nên mỗi ô
 * chỉ được cao bằng bốn dòng chữ nhỏ.
 */
export default function ForecastChip({ day }) {
  const date = new Date(`${day.date}T00:00:00`);

  return (
    <div className="forecast-chip">
      <span className="forecast-chip-dow">
        {date.toLocaleDateString('vi-VN', { weekday: 'short' })}
      </span>
      <span className="forecast-chip-emoji">{day.emoji}</span>
      <span className="forecast-chip-temp">
        {Math.round(day.tempMin)}–{Math.round(day.tempMax)}°
      </span>
      <span className="forecast-chip-rain">☔ {day.rainChance ?? 0}%</span>
    </div>
  );
}
