import { useState } from 'react';
import * as wearPlansApi from '../api/wearPlans';
import { Button, ErrorBanner, Field, Loading, Modal } from '../components/ui';
import { DOW_NAMES, formatDate, toIsoDate, todayIso } from '../utils/date';

const DAY_OPTIONS = [3, 5, 7, 10, 14];

const EXAMPLES = [
  'Lên lịch 7 ngày đi làm ở văn phòng, cần lịch sự nhưng thoải mái',
  '5 ngày du lịch Đà Lạt, trời se lạnh, ưu tiên đồ dễ chụp ảnh',
  '3 ngày cuối tuần: thứ Bảy có tiệc sinh nhật, còn lại đi cà phê',
];

/** Thứ trong tuần của một ngày ISO, theo thứ tự T2..CN như lịch tháng. */
function weekdayOf(iso) {
  const index = (new Date(`${iso}T00:00:00`).getDay() + 6) % 7;
  return DOW_NAMES[index];
}

function isoPlusDays(iso, offset) {
  const date = new Date(`${iso}T00:00:00`);
  date.setDate(date.getDate() + offset);
  return toIsoDate(date);
}

/**
 * Nhập yêu cầu → AI sinh lịch → xem trước → lưu.
 *
 * Ba bước tách bạch trong cùng một modal: người dùng thấy được kế hoạch trước khi nó chạm vào
 * lịch thật, và những ngày họ đã tự đặt chỉ bị thay khi chính họ tick ghi đè.
 */
export default function AiPlanModal({ forecast, tone, onSaved, onClose }) {
  const [step, setStep] = useState('form');
  const [request, setRequest] = useState('');
  const [days, setDays] = useState(7);
  const [startDate, setStartDate] = useState(todayIso());
  const [preview, setPreview] = useState(null);
  const [title, setTitle] = useState('');
  /** Ngày nào được phép ghi đè kế hoạch cũ, khóa theo ISO date. */
  const [overwrite, setOverwrite] = useState({});
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  // Dự báo chỉ có cho vài ngày tới, còn kế hoạch có thể dài tới 14 ngày — cắt theo đúng khoảng
  // người dùng chọn để phần dư không lọt vào prompt.
  const endDate = isoPlusDays(startDate, Number(days) - 1);
  const usableForecast = (forecast ?? []).filter((d) => d.date >= startDate && d.date <= endDate);

  const handleGenerate = async (e) => {
    e.preventDefault();
    if (!request.trim()) {
      setError('Hãy mô tả bạn muốn lên kế hoạch cho dịp gì.');
      return;
    }

    setBusy(true);
    setError(null);
    try {
      const result = await wearPlansApi.generateWearPlan({
        request: request.trim(),
        days: Number(days),
        startDate,
        tone: tone || null,
        // Chỉ gửi những ngày nằm trong đợt. Gửi cả dự báo 7 ngày cho một kế hoạch 3 ngày là
        // nhét vào prompt bốn ngày AI không dùng tới.
        forecast: usableForecast.length > 0 ? usableForecast : null,
      });
      setPreview(result);
      setTitle(result.title || 'Kế hoạch mặc');
      // Mặc định KHÔNG ghi đè: kế hoạch người dùng tự đặt chỉ mất khi họ chủ động đồng ý.
      setOverwrite({});
      setStep('preview');
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const handleSave = async () => {
    setBusy(true);
    setError(null);
    try {
      const saved = await wearPlansApi.saveWearPlan({
        title: title.trim() || 'Kế hoạch mặc',
        userRequest: request.trim(),
        summary: preview.summary,
        days: preview.days.map((day) => ({
          date: day.date,
          outfitId: day.outfit.id,
          note: day.reason,
          replaceExisting: Boolean(overwrite[day.date]),
        })),
      });
      await onSaved(saved);
      onClose();
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  };

  const conflicts = preview?.days.filter((day) => day.existingPlanId) ?? [];
  const skipped = conflicts.filter((day) => !overwrite[day.date]).length;
  const willSave = (preview?.days.length ?? 0) - skipped;

  return (
    <Modal title="✨ Nhờ AI lên kế hoạch mặc" wide onClose={onClose}>
      <ErrorBanner error={error} onDismiss={() => setError(null)} />

      {step === 'form' && (
        <form onSubmit={handleGenerate}>
          <Field label="Bạn muốn lên kế hoạch cho dịp gì?">
            <textarea
              className="nb-input"
              rows={3}
              value={request}
              onChange={(e) => setRequest(e.target.value)}
              placeholder="Ví dụ: 7 ngày đi làm, thứ Sáu có họp với khách nên cần lịch sự hơn"
              maxLength={1000}
              autoFocus
            />
          </Field>

          <p className="ai-plan-hint">
            Viết càng cụ thể càng tốt — nói rõ dịp, nơi đến, thời tiết bạn đoán, hay ngày nào có
            việc đặc biệt. AI chỉ dùng những bộ đã có trong tủ của bạn.
          </p>

          <div className="ai-plan-examples">
            {EXAMPLES.map((example) => (
              <button
                key={example}
                type="button"
                className="ai-plan-example"
                onClick={() => setRequest(example)}
              >
                {example}
              </button>
            ))}
          </div>

          <div className="filter-bar" style={{ marginTop: 12 }}>
            <Field label="Số ngày" className="filter-cell">
              <select className="nb-select" value={days} onChange={(e) => setDays(e.target.value)}>
                {DAY_OPTIONS.map((option) => (
                  <option key={option} value={option}>{option} ngày</option>
                ))}
              </select>
            </Field>
            <Field label="Bắt đầu từ" className="filter-cell">
              <input
                type="date"
                className="nb-input"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
              />
            </Field>
          </div>

          <p className="ai-plan-hint">
            {usableForecast.length === 0
              ? '🌦️ Đợt này chưa có dự báo thời tiết nên AI sẽ bỏ qua tiêu chí đó thay vì đoán bừa.'
              : usableForecast.length < Number(days)
                ? `🌦️ Có dự báo cho ${usableForecast.length}/${days} ngày đầu; những ngày sau AI xếp theo mục đích của bạn.`
                : `🌦️ AI sẽ dựa vào dự báo thời tiết của cả ${days} ngày.`}
          </p>

          <div className="nb-modal-actions">
            <Button type="submit" variant="primary" disabled={busy}>
              {busy ? '🤖 AI đang lên lịch...' : '✨ Tạo kế hoạch'}
            </Button>
            <Button type="button" onClick={onClose} disabled={busy}>Hủy</Button>
          </div>

          {busy && <Loading>AI đang xem tủ đồ và xếp lịch, mất khoảng 10 giây...</Loading>}
        </form>
      )}

      {step === 'preview' && preview && (
        <>
          <Field label="Tên kế hoạch">
            <input
              className="nb-input"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              maxLength={120}
            />
          </Field>

          {preview.summary && <p className="ai-plan-summary">💡 {preview.summary}</p>}

          <div className="ai-plan-days">
            {preview.days.map((day) => {
              const conflicted = Boolean(day.existingPlanId);
              const replacing = Boolean(overwrite[day.date]);
              return (
                <div
                  key={day.date}
                  className={`ai-plan-day ${conflicted && !replacing ? 'is-skipped' : ''}`}
                >
                  <div className="ai-plan-day-head">
                    <span className="ai-plan-day-dow">{weekdayOf(day.date)}</span>
                    <span className="ai-plan-day-date">{formatDate(day.date)}</span>
                  </div>

                  <div className="ai-plan-day-outfit">
                    {day.outfit.imageUrl ? (
                      <img src={day.outfit.imageUrl} alt={day.outfit.name} />
                    ) : (
                      <div className="ai-plan-day-noimg">🧢</div>
                    )}
                    <div style={{ minWidth: 0 }}>
                      <div className="ai-plan-day-name" title={day.outfit.name}>{day.outfit.name}</div>
                      <div className="ai-plan-day-reason">{day.reason}</div>
                    </div>
                  </div>

                  {conflicted && (
                    <label className="ai-plan-conflict">
                      <input
                        type="checkbox"
                        checked={replacing}
                        onChange={(e) =>
                          setOverwrite((prev) => ({ ...prev, [day.date]: e.target.checked }))
                        }
                      />
                      <span>
                        Ngày này đã có <strong>{day.existingOutfitName}</strong>.{' '}
                        {replacing ? 'Sẽ thay bằng bộ trên.' : 'Giữ nguyên, bỏ qua ngày này.'}
                      </span>
                    </label>
                  )}
                </div>
              );
            })}
          </div>

          {conflicts.length > 0 && (
            <p className="ai-plan-hint">
              {skipped > 0
                ? `${skipped} ngày đã có kế hoạch sẵn sẽ được giữ nguyên. Tick vào ngày nào bạn muốn thay.`
                : 'Bạn đã chọn thay toàn bộ những ngày trùng.'}
            </p>
          )}

          <div className="nb-modal-actions">
            <Button variant="primary" onClick={handleSave} disabled={busy || willSave === 0}>
              {busy ? 'Đang lưu...' : `💾 Lưu ${willSave} ngày vào lịch`}
            </Button>
            <Button onClick={() => setStep('form')} disabled={busy}>← Sửa yêu cầu</Button>
            <Button onClick={onClose} disabled={busy}>Hủy</Button>
          </div>

          {willSave === 0 && (
            <p className="ai-plan-hint">
              Mọi ngày đều đang được giữ nguyên nên không có gì để lưu. Tick ít nhất một ngày, hoặc
              đổi ngày bắt đầu rồi tạo lại.
            </p>
          )}
        </>
      )}
    </Modal>
  );
}
