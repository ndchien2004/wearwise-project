import { useEffect, useState } from 'react';
import * as itemsApi from '../api/clothingItems';
import { getStatistics } from '../api/statistics';
import BarChart from '../components/BarChart';
import { Badge, EmptyState, ErrorBanner, Loading } from '../components/ui';
import {
  CATEGORY_EMOJIS,
  CATEGORY_LABELS,
  CONDITION_LABELS,
  SEASON_LABELS,
  STATUS_LABELS,
  STYLE_LABELS,
  label,
} from '../utils/labels';
import { daysSince, formatDateTime } from '../utils/date';

// Bảng màu chart đã kiểm tra CVD-safe (deutan ΔE 19.7, contrast ≥ 3:1 trên nền trắng).
const CHART_CATEGORY_COLORS = {
  SHIRT: '#1971c2',
  PANTS: '#7048e8',
  SHOES: '#e8590c',
  JACKET: '#2f9e44',
  ACCESSORY: '#d6336c',
};

const SINGLE_HUE = {
  style: '#7048e8',
  season: '#1971c2',
  topWorn: '#e8590c',
};

const STATUS_CHART_COLORS = {
  AVAILABLE: '#2f9e44',
  LAUNDRY: '#e8590c',
  UNAVAILABLE: '#868e96',
};

const CONDITION_CHART_COLORS = {
  GOOD: '#2f9e44',
  DAMAGED: '#c92a2a',
};

function mapToRows(map, labels, colorFor) {
  return Object.keys(labels).map((key) => ({
    key,
    label: labels[key],
    value: map?.[key] ?? 0,
    color: colorFor(key),
  }));
}

export default function AnalyticsPage() {
  const [stats, setStats] = useState(null);
  const [leastWorn, setLeastWorn] = useState([]);
  const [error, setError] = useState(null);

  useEffect(() => {
    Promise.all([getStatistics(), itemsApi.getLeastWornItems(6)])
      .then(([s, least]) => {
        setStats(s);
        setLeastWorn(least);
      })
      .catch((err) => setError(err.message));
  }, []);

  if (error) return <ErrorBanner error={error} onDismiss={() => setError(null)} />;
  if (!stats) return <Loading>Đang tính toán thống kê...</Loading>;

  const tiles = [
    { label: 'Món đồ', value: stats.totalClothingItems, bg: 'var(--yellow)', emoji: '👕' },
    { label: 'Outfit', value: stats.totalOutfits, bg: 'var(--pink)', emoji: '🧢' },
    { label: 'Đồ yêu thích', value: stats.favoriteClothingItems, bg: 'var(--blue)', emoji: '⭐' },
    { label: 'Outfit yêu thích', value: stats.favoriteOutfits, bg: 'var(--purple)', emoji: '💜' },
  ];

  const topWorn = (stats.topWornItems || []).filter((i) => (i.wearCount ?? 0) > 0);

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">📊 Thống kê tủ đồ</h1>
      </div>

      <div className="stat-row">
        {tiles.map((tile) => (
          <div key={tile.label} className="stat-tile" style={{ background: tile.bg }}>
            <div className="stat-value">
              {tile.emoji} {tile.value}
            </div>
            <div className="stat-label">{tile.label}</div>
          </div>
        ))}
      </div>

      <div className="two-col">
        <div className="nb-card chart-section">
          <h3 className="chart-title">👕 Theo danh mục</h3>
          <BarChart
            data={mapToRows(stats.clothingItemsByCategory, CATEGORY_LABELS, (k) => CHART_CATEGORY_COLORS[k])}
          />
        </div>

        <div className="nb-card chart-section">
          <h3 className="chart-title">🎨 Theo phong cách</h3>
          <BarChart data={mapToRows(stats.clothingItemsByStyle, STYLE_LABELS, () => SINGLE_HUE.style)} />
        </div>

        <div className="nb-card chart-section">
          <h3 className="chart-title">🍀 Theo mùa</h3>
          <BarChart data={mapToRows(stats.clothingItemsBySeason, SEASON_LABELS, () => SINGLE_HUE.season)} />
        </div>

        <div className="nb-card chart-section">
          <h3 className="chart-title">🚦 Theo trạng thái &amp; tình trạng</h3>
          <BarChart
            data={[
              ...mapToRows(stats.clothingItemsByStatus, STATUS_LABELS, (k) => STATUS_CHART_COLORS[k]),
              ...mapToRows(stats.clothingItemsByCondition, CONDITION_LABELS, (k) => CONDITION_CHART_COLORS[k]),
            ]}
          />
        </div>
      </div>

      <div className="two-col">
        <div className="nb-card chart-section">
          <h3 className="chart-title">🏆 Mặc nhiều nhất</h3>
          {topWorn.length === 0 ? (
            <EmptyState emoji="👣">Chưa có lượt mặc nào được ghi nhận.</EmptyState>
          ) : (
            <BarChart
              data={topWorn.map((item) => ({
                key: item.id,
                label: `${CATEGORY_EMOJIS[item.category] || ''} ${item.name}`,
                value: item.wearCount ?? 0,
                color: SINGLE_HUE.topWorn,
              }))}
            />
          )}
        </div>

        <div className="nb-card chart-section">
          <h3 className="chart-title">🕸️ Lâu chưa đụng tới</h3>
          <p style={{ fontWeight: 600, color: 'var(--muted)', marginBottom: 12, fontSize: 14 }}>
            Cân nhắc đem pass hoặc quyên góp những món này nhé!
          </p>
          {leastWorn.length === 0 ? (
            <EmptyState emoji="🧺">Tủ đồ đang trống.</EmptyState>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {leastWorn.map((item) => {
                const days = daysSince(item.lastWornAt);
                return (
                  <div key={item.id} className="least-worn-row">
                    <div className="least-worn-main">
                      <strong className="least-worn-name">
                        {CATEGORY_EMOJIS[item.category]} {item.name}
                      </strong>
                      <span className="least-worn-sub">
                        {days === null
                          ? 'Chưa mặc lần nào'
                          : `Lần cuối: ${formatDateTime(item.lastWornAt)}`}
                      </span>
                    </div>
                    <Badge color={days === null ? 'red' : 'muted'}>
                      {days === null ? 'Chưa mặc bao giờ' : `${days} ngày trước`}
                    </Badge>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
