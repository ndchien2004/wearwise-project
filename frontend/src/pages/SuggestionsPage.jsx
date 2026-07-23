import { useCallback, useEffect, useState } from 'react';
import * as aiApi from '../api/ai';
import * as outfitsApi from '../api/outfits';
import * as plansApi from '../api/plans';
import { DEFAULT_CITY, getWeather, searchCity } from '../api/weather';
import OutfitVisual from '../components/OutfitVisual';
import { Badge, Button, EmptyState, ErrorBanner, Loading } from '../components/ui';
import {
  CATEGORY_EMOJIS,
  SEASON_EMOJIS,
  SEASON_LABELS,
  STYLE_LABELS,
  SUGGESTION_REASON_LABELS,
  TONE_EMOJIS,
  TONE_LABELS,
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

  const [aiTone, setAiTone] = useState('');
  const [aiSuggestions, setAiSuggestions] = useState(null);
  const [aiLoading, setAiLoading] = useState(false);
  const [aiError, setAiError] = useState(null);
  const [creatingName, setCreatingName] = useState(null);

  const [aiRanking, setAiRanking] = useState(null);
  const [rankingLoading, setRankingLoading] = useState(false);
  const [rankingError, setRankingError] = useState(null);

  const load = useCallback(async (activeCity) => {
    setWeather(null);
    setSuggestions(null);
    setError(null);
    setAiRanking(null); // đổi thành phố thì bỏ xếp hạng AI cũ
    setRankingError(null);
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

  const askAi = async () => {
    setAiError(null);
    setAiSuggestions(null);
    setAiLoading(true);
    try {
      const result = await aiApi.suggestAiOutfits({
        temperature: weather.current.temperature,
        raining: weather.current.raining,
        weatherDescription: weather.current.desc,
        tone: aiTone || null,
      });
      setAiSuggestions(result);
    } catch (err) {
      setAiError(err.message);
    } finally {
      setAiLoading(false);
    }
  };

  // Nhờ AI xếp hạng các outfit CÓ SẴN theo thời tiết (bấm nút mới chạy).
  const rankWithAi = async () => {
    setRankingError(null);
    setRankingLoading(true);
    try {
      const result = await aiApi.rankAiOutfits({
        temperature: weather.current.temperature,
        raining: weather.current.raining,
        weatherDescription: weather.current.desc,
        tone: aiTone || null,
      });
      setAiRanking(result);
    } catch (err) {
      setRankingError(err.message);
    } finally {
      setRankingLoading(false);
    }
  };

  // Biến một gợi ý AI thành outfit thật trong tủ.
  const createOutfitFromAi = async (suggestion) => {
    setAiError(null);
    setCreatingName(suggestion.name);
    try {
      await outfitsApi.createOutfit({
        name: suggestion.name,
        description: (suggestion.reason || 'Bộ đồ do AI gợi ý').slice(0, 1000),
        season: 'ALL_SEASON',
        style: 'CASUAL',
        favorite: false,
        clothingItemIds: suggestion.items.map((item) => item.id),
      });
      setNotice(`Đã tạo outfit "${suggestion.name}" từ gợi ý AI! 🧢`);
    } catch (err) {
      setAiError(err.message);
    } finally {
      setCreatingName(null);
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

          <h2 className="section-heading">🤖 Nhờ AI phối đồ từ tủ của bạn</h2>
          <div className="nb-card" style={{ marginBottom: 20 }}>
            <div className="ai-control-row">
              <div className="nb-field" style={{ margin: 0, flex: '1 1 240px' }}>
                <label className="nb-label">Tone màu muốn mặc</label>
                <select className="nb-select" value={aiTone} onChange={(e) => setAiTone(e.target.value)}>
                  <option value="">🎲 Tùy AI chọn</option>
                  {Object.entries(TONE_LABELS).map(([value, text]) => (
                    <option key={value} value={value}>
                      {TONE_EMOJIS[value]} {text}
                    </option>
                  ))}
                </select>
              </div>
              <Button variant="primary" className="ai-ask-btn" onClick={askAi} disabled={aiLoading}>
                {aiLoading ? '🤖 AI đang phối đồ...' : '✨ Nhờ AI phối đồ'}
              </Button>
            </div>
            <p style={{ fontWeight: 600, color: 'var(--muted)', fontSize: 13.5, marginTop: 10, marginBottom: 0 }}>
              AI sẽ dựa vào thời tiết {Math.round(weather.current.temperature)}°C
              {weather.current.raining ? ' (đang mưa)' : ''} + thuộc tính và màu sắc đồ trong tủ để phối bộ phù hợp nhất.
            </p>

            {aiError && (
              <div style={{ marginTop: 12 }}>
                <ErrorBanner error={aiError} onDismiss={() => setAiError(null)} />
              </div>
            )}

            {aiSuggestions && (
              <div className="card-grid" style={{ marginTop: 16 }}>
                {aiSuggestions.map((suggestion) => (
                  <div key={suggestion.name} className="nb-card item-card">
                    <div className="item-name" title={suggestion.name}>🤖 {suggestion.name}</div>
                    <div className="badge-row">
                      {suggestion.items.map((item) => (
                        <Badge key={item.id}>
                          {CATEGORY_EMOJIS[item.category]} {item.name}
                        </Badge>
                      ))}
                    </div>
                    {suggestion.items.some((item) => item.imageUrl) && (
                      <div className="outfit-collage">
                        {suggestion.items.filter((item) => item.imageUrl).slice(0, 4).map((item) => (
                          <div key={item.id} className="collage-cell" title={item.name}>
                            <img src={item.imageUrl} alt={item.name} />
                          </div>
                        ))}
                      </div>
                    )}
                    <p style={{ fontWeight: 600, fontSize: 13.5, color: 'var(--muted)', margin: 0 }}>
                      💡 {suggestion.reason}
                    </p>
                    <div className="card-actions">
                      <Button
                        size="sm"
                        variant="pink"
                        onClick={() => createOutfitFromAi(suggestion)}
                        disabled={creatingName !== null}
                      >
                        {creatingName === suggestion.name ? '⏳ Đang tạo...' : '🧢 Tạo outfit từ gợi ý'}
                      </Button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="section-heading-row">
            <h2 className="section-heading" style={{ margin: 0 }}>✨ Outfit phù hợp hôm nay</h2>
            {suggestions && suggestions.length > 0 && (
              <Button variant="primary" onClick={rankWithAi} disabled={rankingLoading}>
                {rankingLoading ? '🤖 AI đang chọn...' : '🤖 Để AI chọn giúp'}
              </Button>
            )}
          </div>

          {rankingError && (
            <div style={{ marginBottom: 14 }}>
              <ErrorBanner error={rankingError} onDismiss={() => setRankingError(null)} />
            </div>
          )}

          {aiRanking && (
            <div className="nb-card" style={{ background: 'var(--yellow)', marginBottom: 22 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10, flexWrap: 'wrap', marginBottom: 6 }}>
                <h3 className="chart-title" style={{ margin: 0 }}>🤖 AI đề xuất cho hôm nay</h3>
                <button type="button" onClick={() => setAiRanking(null)} className="nb-btn nb-btn--sm">
                  ✕ Ẩn
                </button>
              </div>
              <div className="card-grid" style={{ marginTop: 8 }}>
                {aiRanking.map(({ outfit, reason }, index) => (
                  <div key={outfit.id} className="nb-card item-card">
                    <OutfitVisual outfit={outfit} />
                    <div className="item-name" title={outfit.name}>
                      {index === 0 ? '🥇 ' : ''}🧢 {outfit.name}
                    </div>
                    <div className="item-meta">
                      {SEASON_EMOJIS[outfit.season]} {label(SEASON_LABELS, outfit.season)} ·{' '}
                      {label(STYLE_LABELS, outfit.style)}
                    </div>
                    <p style={{ fontWeight: 600, fontSize: 13.5, margin: 0 }}>💡 {reason}</p>
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
            </div>
          )}

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
                  <OutfitVisual outfit={outfit} />

                  <div className="item-card-top">
                    <div style={{ minWidth: 0 }}>
                      <div className="item-name" title={outfit.name}>
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
