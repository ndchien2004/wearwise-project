// Open-Meteo: API thời tiết miễn phí, không cần API key.

const WEATHER_CODE_MAP = {
  0: { desc: 'Trời quang', emoji: '☀️', raining: false },
  1: { desc: 'Ít mây', emoji: '🌤️', raining: false },
  2: { desc: 'Có mây', emoji: '⛅', raining: false },
  3: { desc: 'Nhiều mây', emoji: '☁️', raining: false },
  45: { desc: 'Sương mù', emoji: '🌫️', raining: false },
  48: { desc: 'Sương muối', emoji: '🌫️', raining: false },
  51: { desc: 'Mưa phùn nhẹ', emoji: '🌦️', raining: true },
  53: { desc: 'Mưa phùn', emoji: '🌦️', raining: true },
  55: { desc: 'Mưa phùn dày', emoji: '🌧️', raining: true },
  61: { desc: 'Mưa nhỏ', emoji: '🌧️', raining: true },
  63: { desc: 'Mưa vừa', emoji: '🌧️', raining: true },
  65: { desc: 'Mưa to', emoji: '⛈️', raining: true },
  66: { desc: 'Mưa băng nhẹ', emoji: '🌧️', raining: true },
  67: { desc: 'Mưa băng', emoji: '🌧️', raining: true },
  71: { desc: 'Tuyết nhẹ', emoji: '🌨️', raining: false },
  73: { desc: 'Tuyết vừa', emoji: '🌨️', raining: false },
  75: { desc: 'Tuyết dày', emoji: '❄️', raining: false },
  80: { desc: 'Mưa rào nhẹ', emoji: '🌦️', raining: true },
  81: { desc: 'Mưa rào', emoji: '🌧️', raining: true },
  82: { desc: 'Mưa rào dữ dội', emoji: '⛈️', raining: true },
  95: { desc: 'Dông', emoji: '⛈️', raining: true },
  96: { desc: 'Dông kèm mưa đá', emoji: '⛈️', raining: true },
  99: { desc: 'Dông mạnh kèm mưa đá', emoji: '⛈️', raining: true },
};

export function describeWeatherCode(code) {
  return WEATHER_CODE_MAP[code] || { desc: 'Không rõ', emoji: '🌈', raining: false };
}

export async function searchCity(name) {
  const url = `https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(name)}&count=5&language=vi&format=json`;
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error('Không tìm kiếm được thành phố.');
  }
  const data = await response.json();
  return (data.results || []).map((r) => ({
    name: r.name,
    country: r.country,
    admin1: r.admin1,
    latitude: r.latitude,
    longitude: r.longitude,
  }));
}

/**
 * Số ngày dự báo xin từ Open-Meteo. Bằng đúng trần của một đợt kế hoạch mặc
 * (`WearPlan.MAX_DAYS` = 14) chứ không phải số ô dự báo hiển thị trên trang: kế hoạch 14 ngày mà
 * chỉ có 5 ngày dự báo thì AI xếp 9 ngày cuối trong tình trạng mù thời tiết. Gói miễn phí cho tới
 * 16 ngày nên không tốn thêm gì; dải hiển thị tự cắt bớt bằng `FORECAST_STRIP_DAYS`.
 */
const FORECAST_DAYS = 14;

/** Số ô dự báo vẽ trên trang Gợi ý — 14 ô thì tràn hàng và không ai đọc tới ngày thứ mười. */
export const FORECAST_STRIP_DAYS = 5;

export async function getWeather(latitude, longitude) {
  const params = new URLSearchParams({
    latitude: String(latitude),
    longitude: String(longitude),
    current: 'temperature_2m,apparent_temperature,relative_humidity_2m,precipitation,weather_code',
    daily: 'temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code',
    forecast_days: String(FORECAST_DAYS),
    timezone: 'auto',
  });

  const response = await fetch(`https://api.open-meteo.com/v1/forecast?${params}`);
  if (!response.ok) {
    throw new Error('Không lấy được dữ liệu thời tiết.');
  }

  const data = await response.json();
  const currentInfo = describeWeatherCode(data.current.weather_code);

  return {
    current: {
      temperature: data.current.temperature_2m,
      feelsLike: data.current.apparent_temperature,
      humidity: data.current.relative_humidity_2m,
      precipitation: data.current.precipitation,
      code: data.current.weather_code,
      desc: currentInfo.desc,
      emoji: currentInfo.emoji,
      raining: currentInfo.raining || data.current.precipitation > 0,
    },
    daily: data.daily.time.map((date, i) => ({
      date,
      tempMax: data.daily.temperature_2m_max[i],
      tempMin: data.daily.temperature_2m_min[i],
      rainChance: data.daily.precipitation_probability_max[i],
      ...describeWeatherCode(data.daily.weather_code[i]),
    })),
  };
}

export const DEFAULT_CITY = {
  name: 'Hà Nội',
  country: 'Việt Nam',
  latitude: 21.0245,
  longitude: 105.8412,
};
