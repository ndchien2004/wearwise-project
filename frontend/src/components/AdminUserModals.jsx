import { useState } from 'react';
import { Button, ErrorBanner, Field, Modal } from './ui';

/** Các mốc thời gian hay dùng — bấm nhanh thay vì gõ số phút. */
const DURATION_PRESETS = [
  { minutes: 60, label: '1 giờ' },
  { minutes: 60 * 24, label: '1 ngày' },
  { minutes: 60 * 24 * 7, label: '1 tuần' },
  { minutes: 60 * 24 * 30, label: '30 ngày' },
];

/**
 * Hộp thoại khóa tài khoản. Lý do là **bắt buộc** (server cũng bắt buộc): nhật ký kiểm toán chỉ
 * có giá trị khi trả lời được câu "vì sao", và việc phải gõ lý do buộc người bấm nút dừng lại
 * một nhịp trước một hành động ảnh hưởng trực tiếp tới người khác.
 */
export function LockAccountModal({ username, onClose, onSubmit }) {
  const [reason, setReason] = useState('');
  const [durationMinutes, setDurationMinutes] = useState(60 * 24);
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);

  const submit = async () => {
    if (!reason.trim()) {
      setError('Hãy nhập lý do khóa tài khoản.');
      return;
    }

    setSaving(true);
    setError(null);
    try {
      await onSubmit({ reason: reason.trim(), durationMinutes });
    } catch (err) {
      setError(err.message);
      setSaving(false);
    }
  };

  return (
    <Modal title={`🔒 Khóa tài khoản ${username}`} onClose={onClose}>
      <ErrorBanner error={error} onDismiss={() => setError(null)} />

      <p className="muted-note" style={{ marginBottom: 14 }}>
        Tài khoản sẽ mất quyền truy cập <strong>ngay lập tức</strong>: mọi phiên đang mở bị thu hồi
        và người dùng không đăng nhập lại được cho tới khi hết hạn khóa.
      </p>

      <Field label="Lý do (ghi vào nhật ký kiểm toán)">
        <textarea
          className="nb-textarea"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder="Ví dụ: Tải lên hàng loạt ảnh không phải quần áo, đốt hết quota thử đồ."
          maxLength={300}
          autoFocus
        />
      </Field>

      <Field label="Thời gian khóa">
        <div className="admin-preset-row">
          {DURATION_PRESETS.map((preset) => (
            <Button
              key={preset.minutes}
              size="sm"
              variant={durationMinutes === preset.minutes ? 'dark' : undefined}
              onClick={() => setDurationMinutes(preset.minutes)}
            >
              {preset.label}
            </Button>
          ))}
        </div>
        <input
          className="nb-input"
          type="number"
          min={1}
          max={525600}
          value={durationMinutes}
          onChange={(e) => setDurationMinutes(Number(e.target.value))}
          style={{ marginTop: 10 }}
        />
        <span className="muted-note">Đơn vị: phút. Tối đa 525600 (1 năm).</span>
      </Field>

      <div className="nb-modal-actions">
        <Button onClick={onClose} disabled={saving}>
          Hủy
        </Button>
        <Button variant="danger" onClick={submit} disabled={saving}>
          {saving ? 'Đang khóa...' : '🔒 Khóa tài khoản'}
        </Button>
      </div>
    </Modal>
  );
}

export function UnlockAccountModal({ username, onClose, onSubmit }) {
  const [reason, setReason] = useState('');
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);

  const submit = async () => {
    if (!reason.trim()) {
      setError('Hãy nhập lý do mở khóa tài khoản.');
      return;
    }

    setSaving(true);
    setError(null);
    try {
      await onSubmit({ reason: reason.trim() });
    } catch (err) {
      setError(err.message);
      setSaving(false);
    }
  };

  return (
    <Modal title={`🔓 Mở khóa ${username}`} onClose={onClose}>
      <ErrorBanner error={error} onDismiss={() => setError(null)} />

      <Field label="Lý do (ghi vào nhật ký kiểm toán)">
        <textarea
          className="nb-textarea"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder="Ví dụ: Đã xác minh lại, người dùng không vi phạm."
          maxLength={300}
          autoFocus
        />
      </Field>

      <div className="nb-modal-actions">
        <Button onClick={onClose} disabled={saving}>
          Hủy
        </Button>
        <Button variant="green" onClick={submit} disabled={saving}>
          {saving ? 'Đang mở...' : '🔓 Mở khóa'}
        </Button>
      </div>
    </Modal>
  );
}

/**
 * Đặt hạn mức riêng. Ô để trống = dùng mặc định của hệ thống; đó là lý do hai ô này giữ giá trị
 * dạng chuỗi thay vì số — cần phân biệt "để trống" với "đặt bằng 0" (0 nghĩa là chặn hoàn toàn).
 */
export function RateLimitModal({ user, onClose, onSubmit }) {
  const [aiPerHour, setAiPerHour] = useState(user.aiQuotaPerHour ?? '');
  const [externalPerHour, setExternalPerHour] = useState(user.externalQuotaPerHour ?? '');
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);

  const submit = async () => {
    setSaving(true);
    setError(null);
    try {
      await onSubmit({
        aiPerHour: aiPerHour === '' ? null : Number(aiPerHour),
        externalPerHour: externalPerHour === '' ? null : Number(externalPerHour),
      });
    } catch (err) {
      setError(err.message);
      setSaving(false);
    }
  };

  return (
    <Modal title={`🎚️ Hạn mức của ${user.username}`} onClose={onClose}>
      <ErrorBanner error={error} onDismiss={() => setError(null)} />

      <p className="muted-note" style={{ marginBottom: 14 }}>
        Để <strong>trống</strong> nghĩa là dùng mức mặc định của hệ thống. Đặt <strong>0</strong> là
        chặn hoàn toàn nhóm đó. Thay đổi có hiệu lực ngay, không cần khởi động lại server.
      </p>

      <Field label="Lượt gọi AI mỗi giờ (nhận diện ảnh, gợi ý phối đồ)">
        <input
          className="nb-input"
          type="number"
          min={0}
          value={aiPerHour}
          onChange={(e) => setAiPerHour(e.target.value)}
          placeholder="Mặc định"
        />
      </Field>

      <Field label="Lượt thử đồ ảo / tải ảnh mỗi giờ">
        <input
          className="nb-input"
          type="number"
          min={0}
          value={externalPerHour}
          onChange={(e) => setExternalPerHour(e.target.value)}
          placeholder="Mặc định"
        />
      </Field>

      <div className="nb-modal-actions">
        <Button onClick={onClose} disabled={saving}>
          Hủy
        </Button>
        <Button variant="primary" onClick={submit} disabled={saving}>
          {saving ? 'Đang lưu...' : 'Lưu hạn mức'}
        </Button>
      </div>
    </Modal>
  );
}
