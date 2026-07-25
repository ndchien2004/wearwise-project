import { useCallback, useEffect, useMemo, useState } from 'react';
import * as outfitsApi from '../api/outfits';
import * as plansApi from '../api/plans';
import CalendarPlanChip from '../components/CalendarPlanChip';
import { Button, ErrorBanner, Field, Modal } from '../components/ui';
import { useConfirm } from '../context/ConfirmContext';
import { buildMonthGrid, DOW_NAMES, formatDate, MONTH_NAMES, todayIso, toIsoDate } from '../utils/date';

function DayModal({ iso, plans, outfits, onChanged, onClose }) {
  const confirm = useConfirm();
  const [outfitId, setOutfitId] = useState('');
  const [note, setNote] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const dayPlans = plans.filter((p) => p.date === iso);

  const handleAdd = async (e) => {
    e.preventDefault();
    if (!outfitId) {
      setError('Chọn một outfit cho ngày này.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await plansApi.createPlan({ date: iso, outfitId: Number(outfitId), note: note.trim() || null });
      setOutfitId('');
      setNote('');
      await onChanged();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const handleComplete = async (plan) => {
    setBusy(true);
    setError(null);
    try {
      await plansApi.completePlan(plan.id);
      await onChanged();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const handleDelete = async (plan) => {
    const ok = await confirm({
      title: 'Xóa kế hoạch?',
      message: `Bỏ kế hoạch mặc "${plan.outfit.name}" ngày ${formatDate(plan.date)}?`,
      confirmLabel: '🗑️ Xóa luôn',
      danger: true,
    });
    if (!ok) return;
    setBusy(true);
    setError(null);
    try {
      await plansApi.deletePlan(plan.id);
      await onChanged();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal title={`📅 Ngày ${formatDate(iso)}`} onClose={onClose}>
      <ErrorBanner error={error} onDismiss={() => setError(null)} />

      {dayPlans.length === 0 ? (
        <p style={{ fontWeight: 600, color: 'var(--muted)', marginBottom: 16 }}>
          Chưa có kế hoạch nào cho ngày này.
        </p>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 18 }}>
          {dayPlans.map((plan) => (
            <div key={plan.id} className="nb-card nb-card--flat" style={{ padding: 14 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
                <div style={{ minWidth: 0 }}>
                  <strong>{plan.completed ? '✅' : '🕐'} {plan.outfit.name}</strong>
                  {plan.note && (
                    <div style={{ fontSize: 13.5, color: 'var(--muted)', fontWeight: 600 }}>{plan.note}</div>
                  )}
                </div>
                <div style={{ display: 'flex', gap: 8 }}>
                  {!plan.completed && (
                    <Button size="sm" variant="green" disabled={busy} onClick={() => handleComplete(plan)}>
                      ✅ Đã mặc
                    </Button>
                  )}
                  <Button size="sm" variant="danger" disabled={busy} onClick={() => handleDelete(plan)}>
                    🗑️
                  </Button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      <form onSubmit={handleAdd} style={{ borderTop: '2px dashed var(--ink)', paddingTop: 16 }}>
        <h4 style={{ fontSize: 15, marginBottom: 12 }}>➕ Thêm kế hoạch</h4>
        <Field label="Outfit">
          <select className="nb-select" value={outfitId} onChange={(e) => setOutfitId(e.target.value)}>
            <option value="">— Chọn outfit —</option>
            {outfits.map((o) => (
              <option key={o.id} value={o.id}>{o.name}</option>
            ))}
          </select>
        </Field>
        <Field label="Ghi chú">
          <input
            className="nb-input"
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="vd: Họp với khách hàng"
            maxLength={500}
          />
        </Field>
        <div className="nb-modal-actions">
          <button type="submit" className="nb-btn nb-btn--primary" disabled={busy}>
            {busy ? 'Đang lưu...' : '💾 Thêm vào lịch'}
          </button>
        </div>
      </form>
    </Modal>
  );
}

export default function CalendarPage() {
  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth());
  const [plans, setPlans] = useState([]);
  const [outfits, setOutfits] = useState([]);
  const [error, setError] = useState(null);
  const [selectedDay, setSelectedDay] = useState(null);

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
        <h1 className="page-title">📅 Lịch phối đồ</h1>
        <div className="calendar-nav">
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
    </div>
  );
}
