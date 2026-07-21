// Bar chart ngang thuần HTML. Mỗi hàng luôn có nhãn + giá trị hiển thị
// trực tiếp nên màu sắc không bao giờ là kênh thông tin duy nhất.
export default function BarChart({ data }) {
  const max = Math.max(1, ...data.map((d) => d.value));

  return (
    <div>
      {data.map((row) => (
        <div key={row.key} className="bar-row" title={`${row.label}: ${row.value}`}>
          <span className="bar-label">{row.label}</span>
          <div className="bar-track">
            <div
              className={`bar-fill ${row.value === 0 ? 'is-zero' : ''}`}
              style={{
                width: `${(row.value / max) * 100}%`,
                background: row.color,
              }}
            />
          </div>
          <span className="bar-value">{row.value}</span>
        </div>
      ))}
    </div>
  );
}
