import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import * as aiApi from '../api/ai';
import * as outfitsApi from '../api/outfits';
import * as plansApi from '../api/plans';
import { DEFAULT_CITY, FORECAST_STRIP_DAYS, getWeather, searchCity } from '../api/weather';
import AiPlanModal from '../components/AiPlanModal';
import AiPrompt from '../components/AiPrompt';
import ForecastChip from '../components/ForecastChip';
import OutfitActions from '../components/OutfitActions';
import OutfitVisual from '../components/OutfitVisual';
import { Badge, Button, EmptyState, ErrorBanner, Loading, Toast } from '../components/ui';
import {
  CATEGORY_EMOJIS,
  NEGATIVE_SUGGESTION_REASONS,
  SEASON_EMOJIS,
  SEASON_LABELS,
  STYLE_LABELS,
  SUGGESTION_REASON_LABELS,
  TONE_LABELS,
  label,
} from '../utils/labels';
import { todayIso } from '../utils/date';

const CITY_KEY = 'wearwise_city';

/**
 * Ba cách trả lời cùng một câu hỏi "hôm nay mặc gì", nằm trong **một** vùng kết quả có tab.
 *
 * <p>Trước đây ba thứ này là ba mục xếp dọc, mỗi mục một tiêu đề và một thẻ chiếm trọn chiều ngang,
 * nên phải cuộn qua hai màn hình mới thấy danh sách outfit — thứ người dùng vào trang để xem. Hai
 * tab đầu còn hiển thị cùng một loại card (outfit đã có trong tủ) nên xếp dọc là in hai lần gần
 * như cùng một danh sách.</p>
 */
const TABS = [
  { key: 'score', emoji: '✨', label: 'Bộ phù hợp' },
  { key: 'ranked', emoji: '🤖', label: 'AI chọn giúp' },
  { key: 'composed', emoji: '🧩', label: 'AI phối bộ mới' },
];

function loadSavedCity() {
  try {
    const raw = localStorage.getItem(CITY_KEY);
    return raw ? JSON.parse(raw) : DEFAULT_CITY;
  } catch {
    return DEFAULT_CITY;
  }
}

// Lưu kết quả AI vào sessionStorage để không mất khi chuyển tab rồi quay lại.
const ssGet = (key) => {
  try {
    return JSON.parse(sessionStorage.getItem(key) || 'null');
  } catch {
    return null;
  }
};
const ssSet = (key, value) => {
  try {
    if (value == null) sessionStorage.removeItem(key);
    else sessionStorage.setItem(key, JSON.stringify(value));
  } catch {
    /* bỏ qua nếu sessionStorage không dùng được */
  }
};

/**
 * Đọc chuỗi thô. Phải bọc try/catch như hai hàm trên: ở Safari chế độ riêng tư và khi người dùng
 * chặn lưu trữ, chỉ *truy cập* `sessionStorage` đã ném SecurityError — và vì lời gọi này nằm trong
 * bộ khởi tạo `useState`, một lỗi ở đây làm trắng cả trang chứ không chỉ mất giá trị đã lưu.
 */
const ssRaw = (key) => {
  try {
    return sessionStorage.getItem(key) || '';
  } catch {
    return '';
  }
};

export default function SuggestionsPage() {
  const [city, setCity] = useState(loadSavedCity);
  const [cityQuery, setCityQuery] = useState('');
  const [cityResults, setCityResults] = useState([]);
  const [weather, setWeather] = useState(null);
  const [suggestions, setSuggestions] = useState(null);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);

  const [tab, setTab] = useState('score');

  const [aiTone, setAiTone] = useState(() => ssRaw('ww_ai_tone'));
  const [aiSuggestions, setAiSuggestions] = useState(() => ssGet('ww_ai_compose'));
  const [aiLoading, setAiLoading] = useState(false);
  const [aiError, setAiError] = useState(null);
  const [creatingName, setCreatingName] = useState(null);

  const [aiRanking, setAiRanking] = useState(() => ssGet('ww_ai_ranking'));
  const [rankingLoading, setRankingLoading] = useState(false);
  const [rankingError, setRankingError] = useState(null);

  const [aiPlanning, setAiPlanning] = useState(false);

  // Lưu kết quả AI để chuyển trang rồi quay lại vẫn còn. Kế hoạch nhiều ngày không cần lưu như vậy:
  // nó được lưu thẳng vào lịch, xem lại ở trang Lịch hoặc thẻ kế hoạch ở Trang chủ.
  useEffect(() => ssSet('ww_ai_ranking', aiRanking), [aiRanking]);
  useEffect(() => ssSet('ww_ai_compose', aiSuggestions), [aiSuggestions]);
  useEffect(() => {
    try {
      sessionStorage.setItem('ww_ai_tone', aiTone);
    } catch {
      /* bỏ qua */
    }
  }, [aiTone]);

  const toastOk = (message) => setNotice({ message, variant: 'success' });
  const toastErr = (message) => setNotice({ message, variant: 'error' });

  const load = useCallback(async (activeCity) => {
    setWeather(null);
    setSuggestions(null);
    setError(null);
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
    // Đổi thành phố thì kết quả AI cũ không còn phù hợp — xóa đi.
    setAiRanking(null);
  };

  const planToday = async (outfit) => {
    setError(null);
    try {
      await plansApi.createPlan({ date: todayIso(), outfitId: outfit.id, note: 'Theo gợi ý thời tiết' });
      toastOk(`Đã thêm "${outfit.name}" vào lịch hôm nay! 📅`);
    } catch (err) {
      if (err.code === 'PLAN_DUPLICATE') {
        toastOk(`"${outfit.name}" đã có trong lịch hôm nay rồi 📅`);
      } else {
        toastErr(err.message);
      }
    }
  };

  const wearNow = async (outfit) => {
    setError(null);
    try {
      const updated = await outfitsApi.markOutfitWorn(outfit.id);
      setSuggestions((list) =>
        list.map((s) => (s.outfit.id === updated.id ? { ...s, outfit: updated } : s))
      );
      // Tab "AI chọn giúp" hiển thị chính những bộ này, nên phải cập nhật cùng lúc — nếu không, cùng
      // một bộ sẽ hiện "đã mặc" ở tab này và "Mặc luôn" ở tab kia.
      setAiRanking((list) =>
        list == null ? list : list.map((r) => (r.outfit.id === updated.id ? { ...r, outfit: updated } : r))
      );
      toastOk(`Đã ghi nhận bạn mặc "${outfit.name}" hôm nay! 👣`);
    } catch (err) {
      toastErr(err.message);
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
      toastOk(`Đã tạo outfit "${suggestion.name}" từ gợi ý AI! 🧢`);
    } catch (err) {
      setAiError(err.message);
    } finally {
      setCreatingName(null);
    }
  };

  // Dự báo theo đúng dạng AiPlanModal cần, để nó không phải biết cấu trúc của API thời tiết.
  const forecastForPlan = (weather?.daily ?? []).map((d) => ({
    date: d.date,
    tempMin: d.tempMin,
    tempMax: d.tempMax,
    rainChance: d.rainChance,
    description: d.desc,
  }));

  const counts = {
    score: suggestions?.length ?? null,
    ranked: aiRanking?.length ?? null,
    composed: aiSuggestions?.length ?? null,
  };

  /** Nút hành động ở góc phải thanh tab — mỗi tab một việc, nên chỉ hiện đúng việc của tab đang mở. */
  const renderTabAction = () => {
    if (tab === 'ranked' && aiRanking) {
      return (
        <Button size="sm" onClick={rankWithAi} disabled={rankingLoading}>
          {rankingLoading ? 'AI đang chọn...' : '🔄 Cho AI chọn lại'}
        </Button>
      );
    }
    if (tab === 'composed' && aiSuggestions) {
      return (
        <Button size="sm" onClick={askAi} disabled={aiLoading}>
          {aiLoading ? 'AI đang phối...' : '🔄 Phối bộ khác'}
        </Button>
      );
    }
    if (tab === 'score' && suggestions?.length > 0) {
      return <span className="results-note">Xếp theo điểm phù hợp với thời tiết hiện tại</span>;
    }
    return null;
  };

  const renderScoreTab = () => {
    if (suggestions === null) return <Loading>Đang chấm điểm outfit...</Loading>;

    if (suggestions.length === 0) {
      return (
        <EmptyState emoji="🤷">
          Chưa có bộ nào mặc được hôm nay. Hãy tạo vài outfit trong mục "Outfit", hoặc kiểm tra xem
          đồ có đang giặt hết không nhé!
        </EmptyState>
      );
    }

    return (
      <div className="card-grid card-grid--dense">
        {suggestions.map(({ outfit, score, reasons }, index) => (
          <div key={outfit.id} className="nb-card nb-card--hover item-card suggest-card">
            <OutfitVisual outfit={outfit} />

            <div className="suggest-card-head">
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

            <div className="badge-row badge-row--tight">
              {reasons.map((reason) => (
                <Badge key={reason} color={NEGATIVE_SUGGESTION_REASONS.has(reason) ? 'red' : 'green'}>
                  {label(SUGGESTION_REASON_LABELS, reason)}
                </Badge>
              ))}
            </div>

            <OutfitActions outfit={outfit} onPlan={planToday} onWear={wearNow} />
          </div>
        ))}
      </div>
    );
  };

  const renderRankedTab = () => {
    if (!aiRanking) {
      return (
        <AiPrompt
          emoji="🤖"
          title="Để AI chọn giúp hôm nay"
          hint={`AI đọc thời tiết ${Math.round(weather.current.temperature)}°C${
            weather.current.raining ? ' (đang mưa)' : ''
          } cùng màu sắc, phong cách của các bộ đã có rồi xếp thứ tự kèm lý do cho từng bộ.`}
          actionLabel="🤖 Cho AI chọn"
          loading={rankingLoading}
          loadingLabel="AI đang chọn..."
          onRun={rankWithAi}
        />
      );
    }

    return (
      <div className="card-grid card-grid--dense">
        {aiRanking.map(({ outfit, reason }, index) => (
          <div key={outfit.id} className="nb-card nb-card--hover item-card suggest-card">
            <OutfitVisual outfit={outfit} />
            <div className="item-name" title={outfit.name}>
              {index === 0 ? '🥇 ' : ''}
              {outfit.name}
            </div>
            <div className="item-meta">
              {SEASON_EMOJIS[outfit.season]} {label(SEASON_LABELS, outfit.season)} ·{' '}
              {label(STYLE_LABELS, outfit.style)}
            </div>
            <p className="suggest-card-reason">💡 {reason}</p>
            <OutfitActions outfit={outfit} onPlan={planToday} onWear={wearNow} />
          </div>
        ))}
      </div>
    );
  };

  const renderComposedTab = () => {
    if (!aiSuggestions) {
      return (
        <AiPrompt
          emoji="🧩"
          title="Nhờ AI phối bộ mới từ tủ của bạn"
          hint="AI ghép các món lẻ đang có thành bộ chưa từng lưu. Thấy bộ nào hợp thì lưu thành outfit thật bằng một nút."
          actionLabel="🧩 Nhờ AI phối đồ"
          loading={aiLoading}
          loadingLabel="AI đang phối đồ..."
          onRun={askAi}
        />
      );
    }

    return (
      <div className="card-grid card-grid--dense">
        {aiSuggestions.map((suggestion) => (
          <div key={suggestion.name} className="nb-card item-card suggest-card">
            {suggestion.items.some((item) => item.imageUrl) && (
              <div className="outfit-collage">
                {suggestion.items
                  .filter((item) => item.imageUrl)
                  .slice(0, 4)
                  .map((item) => (
                    <div key={item.id} className="collage-cell" title={item.name}>
                      <img src={item.imageUrl} alt={item.name} />
                    </div>
                  ))}
              </div>
            )}
            <div className="item-name" title={suggestion.name}>
              🤖 {suggestion.name}
            </div>
            <div className="badge-row badge-row--tight">
              {suggestion.items.map((item) => (
                <Badge key={item.id}>
                  {CATEGORY_EMOJIS[item.category]} {item.name}
                </Badge>
              ))}
            </div>
            <p className="suggest-card-reason">💡 {suggestion.reason}</p>
            <div className="card-actions">
              <Button
                size="sm"
                variant="pink"
                onClick={() => createOutfitFromAi(suggestion)}
                disabled={creatingName !== null}
              >
                {creatingName === suggestion.name ? 'Đang tạo...' : '🧢 Lưu thành outfit'}
              </Button>
            </div>
          </div>
        ))}
      </div>
    );
  };

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title tilt-right">🌦️ Gợi ý theo thời tiết</h1>
        <div className="header-tools header-tools--inline">
          <div className="city-picker">
            <input
              className="nb-input"
              value={cityQuery}
              onChange={(e) => setCityQuery(e.target.value)}
              placeholder={`📍 ${city.name} — đổi thành phố?`}
              aria-label="Đổi thành phố"
            />
            {cityResults.length > 0 && (
              <div className="nb-card city-picker-results">
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
          {/* Lịch phối đồ chỉ để xem lại nên không cần một mục riêng — mở ngay cạnh ô tìm
              thành phố, quay về bằng nút back ở đầu trang lịch. */}
          <Link to="/calendar" className="nb-btn nb-btn--primary">
            📅 Lịch phối đồ →
          </Link>
        </div>
      </div>

      <Toast
        message={notice?.message}
        variant={notice?.variant || 'success'}
        onDismiss={() => setNotice(null)}
      />
      <ErrorBanner error={error} onDismiss={() => setError(null)} />

      {weather === null ? (
        <Loading>Đang xem trời hôm nay...</Loading>
      ) : (
        <>
          {/* Thời tiết và bảng điều khiển AI đứng cạnh nhau: cả hai đều là "thông tin đầu vào",
              và xếp dọc thì riêng chúng đã chiếm hết màn hình đầu tiên. */}
          <div className="suggest-cockpit">
            <section className="nb-card weather-panel">
              <div className="weather-panel-now">
                <span className="weather-panel-emoji">{weather.current.emoji}</span>
                <div style={{ minWidth: 0 }}>
                  <div className="weather-panel-temp">{Math.round(weather.current.temperature)}°C</div>
                  <div className="weather-panel-place">
                    {weather.current.desc} · {city.name}
                  </div>
                </div>
                <div className="weather-facts">
                  <span className="weather-fact">🌡️ Như {Math.round(weather.current.feelsLike)}°</span>
                  <span className="weather-fact">💧 {weather.current.humidity}%</span>
                  <span className={`weather-fact ${weather.current.raining ? 'is-rain' : ''}`}>
                    {weather.current.raining ? '☔ Đang mưa' : '🌂 Không mưa'}
                  </span>
                </div>
              </div>

              {/* Dải này chỉ vẽ vài ngày đầu; cả 14 ngày dự báo vẫn được gửi cho AI lên kế hoạch. */}
              <div className="forecast-strip">
                {weather.daily.slice(0, FORECAST_STRIP_DAYS).map((day) => (
                  <ForecastChip key={day.date} day={day} />
                ))}
              </div>
            </section>

            <section className="nb-card ai-console">
              <h2 className="ai-console-title">🤖 Trợ lý AI</h2>

              <div className="nb-field" style={{ margin: 0 }}>
                <label className="nb-label">Tone màu muốn mặc</label>
                <select className="nb-select" value={aiTone} onChange={(e) => setAiTone(e.target.value)}>
                  <option value="">Tùy AI chọn</option>
                  {Object.entries(TONE_LABELS).map(([value, text]) => (
                    <option key={value} value={value}>
                      {text}
                    </option>
                  ))}
                </select>
              </div>

              <p className="ai-console-hint">
                Tone này áp dụng cho cả hai tab AI bên dưới và cho kế hoạch nhiều ngày.
              </p>

              <Button variant="primary" onClick={() => setAiPlanning(true)}>
                🗓️ AI lên kế hoạch nhiều ngày
              </Button>
              <p className="ai-console-hint">
                Nói bạn cần lịch cho dịp gì và bao nhiêu ngày — dự báo ở bên cạnh được dùng luôn. Xem
                trước rồi lưu cả đợt vào lịch trong một lần.
              </p>
            </section>
          </div>

          {(rankingError || aiError) && (
            <ErrorBanner
              error={rankingError || aiError}
              onDismiss={() => {
                setRankingError(null);
                setAiError(null);
              }}
            />
          )}

          <div className="results-bar">
            <div className="page-switch page-switch--sm" role="tablist" aria-label="Kiểu gợi ý">
              {TABS.map((item) => (
                <button
                  key={item.key}
                  type="button"
                  role="tab"
                  aria-selected={tab === item.key}
                  className={`page-switch-btn ${tab === item.key ? 'is-active' : ''}`}
                  onClick={() => setTab(item.key)}
                >
                  <span className="page-switch-emoji">{item.emoji}</span>
                  {item.label}
                  {counts[item.key] != null && <span className="tab-count">{counts[item.key]}</span>}
                </button>
              ))}
            </div>

            {renderTabAction()}
          </div>

          {tab === 'score' && renderScoreTab()}
          {tab === 'ranked' && renderRankedTab()}
          {tab === 'composed' && renderComposedTab()}
        </>
      )}

      {aiPlanning && (
        <AiPlanModal
          forecast={forecastForPlan}
          tone={aiTone || null}
          onSaved={(saved) => toastOk(`Đã lưu "${saved.title}" — ${saved.dayCount} ngày vào lịch! 📅`)}
          onClose={() => setAiPlanning(false)}
        />
      )}
    </div>
  );
}
