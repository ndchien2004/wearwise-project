import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import * as itemsApi from '../api/clothingItems';
import * as plansApi from '../api/plans';
import { getStatistics } from '../api/statistics';
import { DEFAULT_CITY, getWeather } from '../api/weather';
import * as wearPlansApi from '../api/wearPlans';
import WearHistoryCard from '../components/WearHistoryCard';
import { ItemPreviewChip, OutfitPreviewChip } from '../components/WearPreview';
import { Badge, ErrorBanner, Loading } from '../components/ui';
import { useAuth } from '../context/AuthContext';
import { daysSince, formatDateTime, todayIso } from '../utils/date';

function loadSavedCity() {
  try {
    const raw = localStorage.getItem('wearwise_city');
    return raw ? JSON.parse(raw) : DEFAULT_CITY;
  } catch {
    return DEFAULT_CITY;
  }
}

export default function DashboardPage() {
  const { username } = useAuth();
  const [stats, setStats] = useState(null);
  const [todayPlans, setTodayPlans] = useState([]);
  const [leastWorn, setLeastWorn] = useState([]);
  const [recentlyWorn, setRecentlyWorn] = useState([]);
  const [weather, setWeather] = useState(null);
  const [activePlans, setActivePlans] = useState([]);
  const [error, setError] = useState(null);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    const today = todayIso();
    Promise.all([
      getStatistics(),
      plansApi.findPlans(today, today),
      itemsApi.getLeastWornItems(4),
      itemsApi.getRecentlyWornItems(4),
      // Đợt kế hoạch là tính năng phụ: hỏng thì trang chủ vẫn phải mở được, nên nuốt lỗi ở đây
      // thay vì để nó kéo cả Promise.all xuống.
      wearPlansApi.findActiveWearPlans().catch(() => []),
    ])
      .then(([s, plans, least, recent, active]) => {
        setStats(s);
        setTodayPlans(plans);
        setLeastWorn(least);
        setRecentlyWorn(recent);
        setActivePlans(active);
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoaded(true));

    const city = loadSavedCity();
    getWeather(city.latitude, city.longitude)
      .then((w) => setWeather({ ...w.current, cityName: city.name }))
      .catch(() => setWeather(null));
  }, []);

  if (!loaded) return <Loading>Đang mở tủ đồ...</Loading>;

  return (
    <div>
      <div className="page-header">
        <div>
          <h1 className="page-title">🏠 Chào, {username}!</h1>
          <p className="page-subtitle">Hôm nay bạn mặc gì?</p>
        </div>
        {weather && (
          <Link to="/suggestions" style={{ textDecoration: 'none' }}>
            <div className="nb-card nb-card--hover" style={{ background: 'var(--cyan)', padding: '12px 18px', display: 'flex', gap: 12, alignItems: 'center' }}>
              <span style={{ fontSize: 34 }}>{weather.emoji}</span>
              <div>
                <strong style={{ fontSize: 20 }}>{Math.round(weather.temperature)}°C</strong>
                <div style={{ fontWeight: 600, fontSize: 13 }}>
                  {weather.desc} · {weather.cityName} → Xem gợi ý
                </div>
              </div>
            </div>
          </Link>
        )}
      </div>

      <ErrorBanner error={error} onDismiss={() => setError(null)} />

      {stats && (
        <div className="stat-row">
          <Link to="/wardrobe" style={{ textDecoration: 'none' }}>
            <div className="stat-tile nb-card--hover" style={{ background: 'var(--yellow)' }}>
              <div className="stat-value">👕 {stats.totalClothingItems}</div>
              <div className="stat-label">Món đồ trong tủ</div>
            </div>
          </Link>
          <Link to="/outfits" style={{ textDecoration: 'none' }}>
            <div className="stat-tile nb-card--hover" style={{ background: 'var(--pink)' }}>
              <div className="stat-value">🧢 {stats.totalOutfits}</div>
              <div className="stat-label">Outfit đã phối</div>
            </div>
          </Link>
          <div className="stat-tile" style={{ background: 'var(--blue)' }}>
            <div className="stat-value">⭐ {stats.favoriteClothingItems + stats.favoriteOutfits}</div>
            <div className="stat-label">Mục yêu thích</div>
          </div>
        </div>
      )}

      {activePlans.map((plan) => {
        const today = todayIso();
        const todayDay = plan.days?.find((day) => day.date === today);
        const percent = plan.dayCount === 0 ? 0 : Math.round((plan.doneCount / plan.dayCount) * 100);

        return (
          <div key={plan.id} className="nb-card wear-plan-card" style={{ marginBottom: 18 }}>
            <h3 className="chart-title" style={{ marginBottom: 4 }}>✨ {plan.title}</h3>
            <div style={{ fontSize: 13, fontWeight: 600 }}>
              {plan.doneCount}/{plan.dayCount} ngày đã mặc · tới {plan.endDate}
            </div>
            <div className="wear-plan-progress">
              <span style={{ width: `${percent}%` }} />
            </div>
            {plan.summary && (
              <div style={{ fontSize: 13, fontWeight: 600, lineHeight: 1.5 }}>💡 {plan.summary}</div>
            )}

            {todayDay ? (
              <div className="wear-plan-today">
                {todayDay.outfit.imageUrl && (
                  <img src={todayDay.outfit.imageUrl} alt={todayDay.outfit.name} />
                )}
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontWeight: 800 }}>
                    {todayDay.completed ? '✅' : '🕐'} Hôm nay: {todayDay.outfit.name}
                  </div>
                  {todayDay.reason && (
                    <div style={{ fontSize: 12.5, color: 'var(--muted)', lineHeight: 1.4 }}>
                      {todayDay.reason}
                    </div>
                  )}
                </div>
              </div>
            ) : (
              <div style={{ fontSize: 13, fontWeight: 600, marginTop: 8 }}>
                Đợt này không xếp bộ nào cho hôm nay.
              </div>
            )}

            <Link to="/calendar" className="nb-btn nb-btn--sm" style={{ marginTop: 10 }}>
              📅 Xem cả đợt trong lịch →
            </Link>
          </div>
        );
      })}

      <div className="two-col">
        <div className="nb-card">
          <h3 className="chart-title">📅 Kế hoạch hôm nay</h3>
          {todayPlans.length === 0 ? (
            <div>
              <p style={{ fontWeight: 600, color: 'var(--muted)', marginBottom: 14 }}>
                Chưa có kế hoạch nào cho hôm nay.
              </p>
              <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                <Link to="/calendar" className="nb-btn nb-btn--primary nb-btn--sm">
                  📅 Mở lịch
                </Link>
                <Link to="/suggestions" className="nb-btn nb-btn--blue nb-btn--sm">
                  🌦️ Xem gợi ý thời tiết
                </Link>
              </div>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {todayPlans.map((plan) => (
                <div key={plan.id} className="nb-card nb-card--flat" style={{ padding: '10px 14px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10, flexWrap: 'wrap', alignItems: 'center' }}>
                    <OutfitPreviewChip
                      outfit={plan.outfit}
                      meta={plan.completed ? '✅ Đã mặc' : '🕐 Chưa mặc'}
                      note={plan.note}
                      className="wear-chip wear-chip--strong"
                    >
                      {plan.completed ? '✅' : '🕐'} {plan.outfit.name}
                    </OutfitPreviewChip>
                    <div className="badge-row">
                      {plan.outfit.clothingItems.slice(0, 4).map((item) => (
                        <ItemPreviewChip key={item.id} item={item} className="wear-chip wear-chip--badge" />
                      ))}
                    </div>
                  </div>
                  {plan.note && (
                    <div style={{ fontSize: 13.5, fontWeight: 600, color: 'var(--muted)', marginTop: 4 }}>
                      📝 {plan.note}
                    </div>
                  )}
                </div>
              ))}
              <Link to="/calendar" className="nb-btn nb-btn--sm" style={{ alignSelf: 'flex-start' }}>
                📅 Xem cả lịch →
              </Link>
            </div>
          )}
        </div>

        <div className="nb-card">
          <h3 className="chart-title">👣 Mặc gần đây</h3>
          {recentlyWorn.length === 0 ? (
            <p style={{ fontWeight: 600, color: 'var(--muted)' }}>
              Chưa ghi nhận lượt mặc nào. Bấm "Mặc hôm nay" trên món đồ hoặc outfit nhé!
            </p>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {recentlyWorn.map((item) => (
                <div key={item.id} className="mini-row">
                  <ItemPreviewChip item={item} className="wear-chip mini-row-name" />
                  <span className="mini-row-value">{formatDateTime(item.lastWornAt)}</span>
                </div>
              ))}
            </div>
          )}

          <h3 className="chart-title" style={{ marginTop: 22 }}>🕸️ Lâu chưa đụng tới</h3>
          {leastWorn.length === 0 ? (
            <p style={{ fontWeight: 600, color: 'var(--muted)' }}>Tủ đồ đang trống.</p>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {leastWorn.map((item) => {
                const days = daysSince(item.lastWornAt);
                return (
                  <div key={item.id} className="mini-row">
                    <ItemPreviewChip item={item} className="wear-chip mini-row-name" />
                    <Badge color={days === null ? 'red' : 'orange'}>
                      {days === null ? 'Chưa mặc bao giờ' : `${days} ngày trước`}
                    </Badge>
                  </div>
                );
              })}
              <Link to="/profile" className="nb-btn nb-btn--sm" style={{ alignSelf: 'flex-start', marginTop: 6 }}>
                📊 Xem thống kê →
              </Link>
            </div>
          )}
        </div>
      </div>

      <WearHistoryCard />
    </div>
  );
}
