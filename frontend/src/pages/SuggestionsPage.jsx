import { useCallback, useEffect, useState } from 'react';
import * as outfitsApi from '../api/outfits';
import * as plansApi from '../api/plans';
import { DEFAULT_CITY, getWeather, searchCity } from '../api/weather';
import { Badge, Button, EmptyState, ErrorBanner, Loading } from '../components/ui';
import {
  CATEGORY_EMOJIS,
  SEASON_EMOJIS,
  SEASON_LABELS,
  STYLE_LABELS,
  SUGGESTION_REASON_LABELS,
  label,
} from '../utils/labels';
import { isWornToday, todayIso } from '../utils/date';

const CITY_KEY = 'wearwise_city';

function loadSavedCity() {
  try {
    const raw = localStorage.getItem(CITY_KEY);
    return raw ? JSON.parse(raw) : DEFAULT_CITY;
  } catch {
    return DEFAULT_CITY;
  }
}

export default function SuggestionsPage() {
  const [city, setCity] = useState(loadSavedCity);
  const [cityQuery, setCityQuery] = useState('');
  const [cityResults, setCityResults] = useState([]);
  const [weather, setWeather] = useState(null);
  const [suggestions, setSuggestions] = useState(null);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);

  const load = useCallback(async (activeCity) => {
    setWeather(null);
    setSuggestions(null);
    setError(null);
    try {
      const w = await getWeather(activeCity.latitude, activeCity.longitude);
      setWeather(w);
      const s = await outfitsApi.suggestOutfits(w.current.temperature, w.current.raining, 6);
      setSuggestions(s);
    } catch (err) {
      setError(err.message);
    }
  }, []);

  useEffect(() => {
    load(city);
  }, [city, load]);

  useEffect(() => {
    const q = cityQuery.trim();
    if (q.length < 2) {
      setCityResults([]);
      return undefined;
    }
    const timer = setTimeout(async () => {
      try {
        setCityResults(await searchCity(q));
      } catch {
        setCityResults([]);
      }
    }, 350);
    return () => clearTimeout(timer);
  }, [cityQuery]);

  const pickCity = (c) => {
    setCity(c);
    localStorage.setItem(CITY_KEY, JSON.stringify(c));
    setCityQuery('');
    setCityResults([]);
  };

  const planToday = async (outfit) => {
    setNotice(null);
    setError(null);
    try {
      await plansApi.createPlan({ date: todayIso(), outfitId: outfit.id, note: 'Theo gợi ý thời tiết' });
      setNotice(`Đã thêm "${outfit.name}" vào lịch hôm nay! 📅`);
    } catch (err) {
      if (err.message?.includes('already planned')) {
        setNotice(`"${outfit.name}" đã có trong lịch hôm nay rồi 📅`);
      } else {
        setError(err.message);
      }
    }
  };

  const wearNow = async (outfit) => {
    setNotice(null);
    setError(null);
    try {
      const updated = await outfitsApi.markOutfitWorn(outfit.id);
      setSuggestions((list) =>
        list.map((s) => (s.outfit.id === updated.id ? { ...s, outfit: updated } : s))
      );
      setNotice(`Đã ghi nhận bạn mặc "${outfit.name}" hôm nay! 👣`);
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title tilt-right">🌦️ Gợi ý theo thời tiết</h1>
        <div style={{ position: 'relative', minWidth: 260 }}>
          <input
            className="nb-input"
            value={cityQuery}
            onChange={(e) => setCityQuery(e.target.value)}
            placeholder={`📍 ${city.name} — đổi thành phố?`}
          />
          {cityResults.length > 0 && (
            <div
              className="nb-card"
              style={{ position: 'absolute', top: '110%', left: 0, right: 0, zIndex: 50, padding: 8 }}
            >
              {cityResults.map((c, i) => (
                <div key={`${c.latitude}-${c.longitude}-${i}`} className="picker-row" onClick={() => pickCity(c)}>
                  📍 {c.name}
                  <span style={{ color: 'var(--muted)', fontSize: 12.5 }}>
                    {[c.admin1, c.country].filter(Boolean).join(', ')}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      <ErrorBanner error={error} onDismiss={() => setError(null)} />
      {notice && (
        <div className="error-banner" style={{ background: 'var(--green)' }}>
          {notice}
          <button
            type="button"
            onClick={() => setNotice(null)}
            style={{ float: 'right', background: 'none', border: 'none', cursor: 'pointer', fontWeight: 900 }}
          >
            ✕
          </button>
        </div>
      )}

      {weather === null ? (
        <Loading>Đang xem trời hôm nay...</Loading>
      ) : (
        <>
          <div className="nb-card weather-hero" style={{ marginBottom: 24, background: 'var(--cyan)' }}>
            <span className="weather-emoji">{weather.current.emoji}</span>
            <div>
              <div className="weather-temp">{Math.round(weather.current.temperature)}°C</div>
              <div className="weather-desc">
                {weather.current.desc} · {city.name}
              </div>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              <Badge>🌡️ Cảm giác như {Math.round(weather.current.feelsLike)}°C</Badge>
              <Badge>💧 Độ ẩm {weather.current.humidity}%</Badge>
              <Badge color={weather.current.raining ? 'blue' : 'green'}>
                {weather.current.raining ? '☔ Đang mưa — nhớ mang áo khoác/ô!' : '🌂 Không mưa'}
              </Badge>
            </div>
          </div>

          <div className="stat-row">
            {weather.daily.map((day) => (
              <div key={day.date} className="stat-tile" style={{ background: 'var(--paper)' }}>
                <div style={{ fontWeight: 700, fontSize: 13 }}>
                  {new Date(`${day.date}T00:00:00`).toLocaleDateString('vi-VN', { weekday: 'short', day: '2-digit', month: '2-digit' })}
                </div>
                <div style={{ fontSize: 30 }}>{day.emoji}</div>
                <div className="stat-label">
                  {Math.round(day.tempMin)}° – {Math.round(day.tempMax)}° · ☔ {day.rainChance ?? 0}%
                </div>
              </div>
            ))}
          </div>

          <h2 className="section-heading">✨ Outfit phù hợp hôm nay</h2>

          {suggestions === null ? (
            <Loading>Đang chấm điểm outfit...</Loading>
          ) : suggestions.length === 0 ? (
            <EmptyState emoji="🤷">
              Chưa có outfit nào để gợi ý. Hãy tạo vài outfit trong mục "Outfit" trước nhé!
            </EmptyState>
          ) : (
            <div className="card-grid">
              {suggestions.map(({ outfit, score, reasons }, index) => (
                <div key={outfit.id} className="nb-card nb-card--hover item-card">
                  <div className="item-card-top">
                    <div style={{ minWidth: 0 }}>
                      <div className="item-name">
                        {index === 0 ? '🏆 ' : ''}
                        {outfit.name}
                      </div>
                      <div className="item-meta">
                        {SEASON_EMOJIS[outfit.season]} {label(SEASON_LABELS, outfit.season)} ·{' '}
                        {label(STYLE_LABELS, outfit.style)}
                      </div>
                    </div>
                    <span className="suggestion-score" title="Điểm phù hợp">
                      {score}
                    </span>
                  </div>

                  <div className="badge-row">
                    {reasons.map((reason) => (
                      <Badge key={reason} color={reason.includes('MISMATCH') || reason.includes('UNAVAILABLE') || reason.includes('NO_JACKET') ? 'red' : 'green'}>
                        {label(SUGGESTION_REASON_LABELS, reason)}
                      </Badge>
                    ))}
                  </div>

                  <div className="badge-row">
                    {outfit.clothingItems.map((item) => (
                      <Badge key={item.id}>
                        {CATEGORY_EMOJIS[item.category]} {item.name}
                      </Badge>
                    ))}
                  </div>

                  <div className="card-actions">
                    <Button size="sm" variant="primary" onClick={() => planToday(outfit)}>
                      📅 Lên lịch hôm nay
                    </Button>
                    {isWornToday(outfit.lastWornAt) ? (
                      <Button size="sm" disabled>
                        ✅ Đã mặc hôm nay
                      </Button>
                    ) : (
                      <Button size="sm" variant="green" onClick={() => wearNow(outfit)}>
                        👣 Mặc luôn
                      </Button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
}
