import { useEffect, useMemo, useState } from 'react';
import { getWearHistory } from '../api/statistics';
import { ItemPreviewChip, OutfitPreviewChip } from './WearPreview';
import { Badge, Button, EmptyState, ErrorBanner, Loading } from './ui';
import { MONTH_NAMES, formatDate, toIsoDate } from '../utils/date';

/** Số lượt mặc theo từng ngày trong tháng. Ngày trống vẫn có cột để nhìn ra được quãng bỏ trống. */
function DayStrip({ days }) {
  const max = Math.max(1, ...days.map((day) => day.total));

  return (
    <div className="wear-day-strip">
      {days.map((day) => (
        <span
          key={day.iso}
          className={`wear-day-bar ${day.total === 0 ? 'is-empty' : ''} ${day.isToday ? 'is-today' : ''}`}
          title={`${formatDate(day.iso)}: ${day.total} lượt`}
        >
          <span className="wear-day-fill" style={{ height: `${(day.total / max) * 100}%` }} />
        </span>
      ))}
    </div>
  );
}

export default function WearHistoryCard() {
  const now = useMemo(() => new Date(), []);
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth());
  const [history, setHistory] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const range = useMemo(() => {
    const lastDay = new Date(year, month + 1, 0);
    return {
      from: toIsoDate(new Date(year, month, 1)),
      to: toIsoDate(lastDay),
      dayCount: lastDay.getDate(),
    };
  }, [year, month]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    getWearHistory({ from: range.from, to: range.to })
      .then((result) => {
        if (!cancelled) setHistory(result);
      })
      .catch((err) => {
        if (!cancelled) setError(err.message);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [range]);

  const days = useMemo(() => {
    const byDate = new Map((history?.daily ?? []).map((day) => [day.date, day]));
    const today = toIsoDate(new Date());

    return Array.from({ length: range.dayCount }, (unused, index) => {
      const iso = toIsoDate(new Date(year, month, index + 1));
      const entry = byDate.get(iso);
      return {
        iso,
        total: entry ? entry.itemWears + entry.outfitWears : 0,
        isToday: iso === today,
      };
    });
  }, [history, range.dayCount, year, month]);

  const changeMonth = (delta) => {
    const next = new Date(year, month + delta, 1);
    setYear(next.getFullYear());
    setMonth(next.getMonth());
  };

  const isThisMonth = year === now.getFullYear() && month === now.getMonth();
  const hasHistory = history && (history.itemWears > 0 || history.outfitWears > 0);

  return (
    <div className="nb-card">
      <div className="wear-history-head">
        <h3 className="chart-title" style={{ margin: 0 }}>📈 Lịch sử mặc</h3>
        <div className="calendar-nav">
          <Button size="sm" onClick={() => changeMonth(-1)}>←</Button>
          <span className="calendar-month-label">{MONTH_NAMES[month]} {year}</span>
          <Button size="sm" onClick={() => changeMonth(1)} disabled={isThisMonth}>→</Button>
        </div>
      </div>

      <ErrorBanner error={error} onDismiss={() => setError(null)} />

      {loading ? (
        <Loading>Đang đọc nhật ký...</Loading>
      ) : !hasHistory ? (
        <EmptyState emoji="🗓️">
          {isThisMonth
            ? 'Tháng này chưa ghi nhận lượt mặc nào. Bấm "Mặc hôm nay" trên món đồ hoặc outfit nhé!'
            : `Không có lượt mặc nào trong ${MONTH_NAMES[month].toLowerCase()}.`}
        </EmptyState>
      ) : (
        <>
          <div className="stat-row wear-history-stats">
            <div className="stat-tile" style={{ background: 'var(--yellow)' }}>
              <div className="stat-value">👣 {history.itemWears}</div>
              <div className="stat-label">Lượt mặc món đồ</div>
            </div>
            <div className="stat-tile" style={{ background: 'var(--pink)' }}>
              <div className="stat-value">🧢 {history.outfitWears}</div>
              <div className="stat-label">Lượt mặc nguyên bộ</div>
            </div>
            <div className="stat-tile" style={{ background: 'var(--cyan)' }}>
              <div className="stat-value">📆 {history.activeDays}/{range.dayCount}</div>
              <div className="stat-label">Ngày có mặc đồ</div>
            </div>
          </div>

          <DayStrip days={days} />

          <h4 className="wear-rank-title">🔥 Mặc nhiều nhất</h4>
          {history.topItems.length === 0 ? (
            <p className="wear-rank-empty">Chưa có lượt mặc món đồ lẻ nào trong tháng.</p>
          ) : (
            <div className="wear-rank-list">
              {history.topItems.map((top) => (
                <div key={top.item.id} className="wear-rank-row">
                  <ItemPreviewChip item={top.item} meta={`${top.wearCount} lượt trong tháng`} />
                  <Badge color="orange">{top.wearCount} lượt</Badge>
                </div>
              ))}
            </div>
          )}

          <h4 className="wear-rank-title">🧢 Bộ dùng nhiều nhất</h4>
          {history.topOutfits.length === 0 ? (
            <p className="wear-rank-empty">Chưa mặc nguyên bộ nào trong tháng.</p>
          ) : (
            <div className="wear-rank-list">
              {history.topOutfits.map((top) => (
                <div key={top.outfit.id} className="wear-rank-row">
                  <OutfitPreviewChip
                    outfit={top.outfit}
                    meta={`${top.wearCount} lượt trong tháng`}
                  >
                    🧢 {top.outfit.name}
                  </OutfitPreviewChip>
                  <Badge color="purple">{top.wearCount} lượt</Badge>
                </div>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
}
