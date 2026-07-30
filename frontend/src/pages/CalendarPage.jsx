import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import * as outfitsApi from '../api/outfits';
import * as plansApi from '../api/plans';
import AiPlanModal from '../components/AiPlanModal';
import CalendarPlanChip from '../components/CalendarPlanChip';
import DayModal from '../components/DayModal';
import { Button, ErrorBanner } from '../components/ui';
import { buildMonthGrid, DOW_NAMES, MONTH_NAMES, todayIso, toIsoDate } from '../utils/date';

export default function CalendarPage() {
  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth());
  const [plans, setPlans] = useState([]);
  const [outfits, setOutfits] = useState([]);
  const [error, setError] = useState(null);
  const [selectedDay, setSelectedDay] = useState(null);
  const [aiPlanning, setAiPlanning] = useState(false);

  const cells = useMemo(() => buildMonthGrid(year, month), [year, month]);
  const today = todayIso();

  const load = useCallback(async () => {
    try {
      const start = toIsoDate(cells[0].date);
      const end = toIsoDate(cells[cells.length - 1].date);
      setPlans(await plansApi.findPlans(start, end));
    } catch (err) {
      setError(err.message);
    }
  }, [cells]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    outfitsApi
      .findOutfits()
      .then(setOutfits)
      .catch((err) => setError(err.message));
  }, []);

  const changeMonth = (delta) => {
    const next = new Date(year, month + delta, 1);
    setYear(next.getFullYear());
    setMonth(next.getMonth());
  };

  const plansByDay = useMemo(() => {
    const map = {};
    plans.forEach((p) => {
      (map[p.date] = map[p.date] || []).push(p);
    });
    return map;
  }, [plans]);

  return (
    <div>
      <div className="page-header">
        <div className="page-heading-group">
          <Link to="/suggestions" className="nb-btn nb-btn--sm">
            ← Gợi ý thời tiết
          </Link>
          <h1 className="page-title">📅 Lịch phối đồ</h1>
        </div>
        <div className="calendar-nav">
          <Button size="sm" variant="green" onClick={() => setAiPlanning(true)}>
            ✨ AI lên kế hoạch
          </Button>
          <Button size="sm" onClick={() => changeMonth(-1)}>← Trước</Button>
          <span className="calendar-month-label">
            {MONTH_NAMES[month]} {year}
          </span>
          <Button size="sm" onClick={() => changeMonth(1)}>Sau →</Button>
          <Button
            size="sm"
            variant="primary"
            onClick={() => {
              setYear(now.getFullYear());
              setMonth(now.getMonth());
            }}
          >
            Hôm nay
          </Button>
        </div>
      </div>

      <ErrorBanner error={error} onDismiss={() => setError(null)} />

      <div className="calendar-grid" style={{ marginBottom: 8 }}>
        {DOW_NAMES.map((d) => (
          <div key={d} className="calendar-dow">{d}</div>
        ))}
      </div>

      <div className="calendar-grid">
        {cells.map((cell) => {
          const dayPlans = plansByDay[cell.iso] || [];
          return (
            <div
              key={cell.iso}
              className={[
                'calendar-cell',
                cell.inMonth ? '' : 'is-outside',
                cell.iso === today ? 'is-today' : '',
              ].join(' ')}
              onClick={() => setSelectedDay(cell.iso)}
            >
              <span className="calendar-daynum">{cell.date.getDate()}</span>
              {dayPlans.slice(0, 3).map((plan) => (
                <CalendarPlanChip key={plan.id} plan={plan} />
              ))}
              {dayPlans.length > 3 && (
                <span style={{ fontSize: 11, fontWeight: 700 }}>+{dayPlans.length - 3} nữa</span>
              )}
            </div>
          );
        })}
      </div>

      {selectedDay && (
        <DayModal
          iso={selectedDay}
          plans={plans}
          outfits={outfits}
          onChanged={load}
          onClose={() => setSelectedDay(null)}
        />
      )}

      {aiPlanning && (
        <AiPlanModal
          onSaved={async (saved) => {
            // Nhảy tới tháng chứa kế hoạch vừa lưu, nếu không người dùng bấm xong chẳng thấy gì
            // thay đổi vì đợt kế hoạch rơi sang tháng sau.
            const start = new Date(`${saved.startDate}T00:00:00`);
            setYear(start.getFullYear());
            setMonth(start.getMonth());
            await load();
          }}
          onClose={() => setAiPlanning(false)}
        />
      )}
    </div>
  );
}
