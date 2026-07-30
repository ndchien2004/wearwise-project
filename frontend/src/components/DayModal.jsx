import { useState } from 'react';
import * as plansApi from '../api/plans';
import { Button, ErrorBanner, Field, Modal } from './ui';
import { useConfirm } from '../context/ConfirmContext';
import { formatDate, todayIso } from '../utils/date';
import { describeBlockers } from '../utils/labels';

/**
 * Chi tiết một ngày trong lịch: danh sách kế hoạch của ngày đó + form thêm kế hoạch mới.
 *
 * <p>Hai luật của mục 4.12 nằm ở đây, đừng gộp lại: ô chọn outfit lọc theo `available` (bộ thiếu
 * món thì không lên lịch được cho bất kỳ ngày nào), còn nút "Đã mặc" khóa theo `wearableNow` (đồ
 * đang giặt là tạm thời, mai giặt xong lại bấm được nên kế hoạch không bị xóa).</p>
 */
export default function DayModal({ iso, plans, outfits, onChanged, onClose }) {
  const confirm = useConfirm();
  const [outfitId, setOutfitId] = useState('');
  const [note, setNote] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const dayPlans = plans.filter((p) => p.date === iso);
  // An outfit missing an archived item cannot be planned — the server rejects it on submit.
  const selectableOutfits = outfits.filter((o) => o.available);
  const isFuture = iso > todayIso();

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

  const handleUncomplete = async (plan) => {
    setBusy(true);
    setError(null);
    try {
      await plansApi.uncompletePlan(plan.id);
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
          {dayPlans.map((plan) => {
            // Bộ vướng đồ giặt / hư hỏng thì server từ chối ghi lượt mặc. Chặn ngay trên nút kèm lý
            // do, thay vì để người dùng bấm rồi mới nhận thông báo lỗi.
            const notWearable = plan.outfit.wearableNow === false;
            const blockerText = describeBlockers(plan.outfit.blockingItems);

            return (
              <div key={plan.id} className="nb-card nb-card--flat" style={{ padding: 14 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
                  <div style={{ minWidth: 0 }}>
                    <strong>{plan.completed ? '✅' : '🕐'} {plan.outfit.name}</strong>
                    {plan.note && (
                      <div style={{ fontSize: 13.5, color: 'var(--muted)', fontWeight: 600 }}>{plan.note}</div>
                    )}
                    {!plan.completed && notWearable && blockerText && (
                      <div className="day-plan-blocker">{blockerText}</div>
                    )}
                  </div>
                  <div style={{ display: 'flex', gap: 8 }}>
                    {plan.completed ? (
                      <Button size="sm" disabled={busy} onClick={() => handleUncomplete(plan)}>
                        ↩️ Bỏ đánh dấu
                      </Button>
                    ) : (
                      <Button
                        size="sm"
                        variant="green"
                        disabled={busy || isFuture || notWearable}
                        title={
                          isFuture
                            ? 'Chưa tới ngày này nên chưa đánh dấu được.'
                            : notWearable
                              ? blockerText || 'Bộ này hôm nay chưa mặc được.'
                              : undefined
                        }
                        onClick={() => handleComplete(plan)}
                      >
                        {notWearable ? '⚠️ Chưa mặc được' : '✅ Đã mặc'}
                      </Button>
                    )}
                    <Button size="sm" variant="danger" disabled={busy} onClick={() => handleDelete(plan)}>
                      🗑️
                    </Button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      <form onSubmit={handleAdd} style={{ borderTop: '2px dashed var(--ink)', paddingTop: 16 }}>
        <h4 style={{ fontSize: 15, marginBottom: 12 }}>➕ Thêm kế hoạch</h4>
        <Field label="Outfit">
          <select className="nb-select" value={outfitId} onChange={(e) => setOutfitId(e.target.value)}>
            <option value="">— Chọn outfit —</option>
            {selectableOutfits.map((o) => (
              <option key={o.id} value={o.id}>{o.name}</option>
            ))}
          </select>
        </Field>
        {selectableOutfits.length < outfits.length && (
          <p style={{ fontSize: 13, color: 'var(--muted)', fontWeight: 600, marginTop: -6, marginBottom: 12 }}>
            {outfits.length - selectableOutfits.length} outfit đang thiếu món nên không lên lịch được.
          </p>
        )}
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
