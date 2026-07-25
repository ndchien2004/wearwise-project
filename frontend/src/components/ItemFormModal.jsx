import { useEffect, useRef, useState } from 'react';
import { Button, ErrorBanner, Field, Modal } from './ui';
import { analyzeClothingImage, getAiStatus } from '../api/ai';
import { uploadClothingImage } from '../api/images';
import { shrinkImageForAi } from '../utils/image';
import {
  CATEGORY_LABELS,
  CONDITION_LABELS,
  SEASON_LABELS,
  STATUS_LABELS,
  STYLE_LABELS,
  TONE_LABELS,
} from '../utils/labels';

const EMPTY_FORM = {
  name: '',
  color: '',
  colorTone: '',
  category: 'SHIRT',
  season: 'ALL_SEASON',
  style: 'CASUAL',
  condition: 'GOOD',
  status: 'AVAILABLE',
  favorite: false,
  imageUrl: '',
};

// Bộ ảnh mẫu vẽ sẵn trong frontend/public/samples/
const SAMPLE_IMAGES = [
  { url: '/samples/ao-thun-trang.svg', label: 'Áo thun trắng' },
  { url: '/samples/quan-jean-xanh.svg', label: 'Quần jean xanh' },
  { url: '/samples/giay-sneaker.svg', label: 'Giày sneaker' },
  { url: '/samples/mu-luoi-trai.svg', label: 'Mũ lưỡi trai' },
  { url: '/samples/outfit-phoi-do.svg', label: 'Ảnh phối đồ' },
];

export default function ItemFormModal({ item, onSave, onClose }) {
  const [form, setForm] = useState(() =>
    item
      ? {
          name: item.name ?? '',
          color: item.color ?? '',
          colorTone: item.colorTone ?? '',
          category: item.category,
          season: item.season,
          style: item.style,
          condition: item.condition,
          status: item.status,
          favorite: Boolean(item.favorite),
          imageUrl: item.imageUrl ?? '',
        }
      : EMPTY_FORM
  );
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [aiReady, setAiReady] = useState(false);
  const [analyzing, setAnalyzing] = useState(false);
  const [aiFilled, setAiFilled] = useState(null);
  const [dragging, setDragging] = useState(false);
  const [showUrlInput, setShowUrlInput] = useState(false);
  const fileInputRef = useRef(null);
  // Giữ lại file vừa chọn để bấm "Điền lại" không phải chọn ảnh lần nữa.
  const lastFileRef = useRef(null);

  useEffect(() => {
    getAiStatus()
      .then((status) => setAiReady(Boolean(status.geminiConfigured)))
      .catch(() => setAiReady(false));
  }, []);

  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }));

  const handlePickFile = () => fileInputRef.current?.click();

  /** Đổ kết quả AI vào form, bỏ qua trường AI không đoán được. */
  const applySuggestion = (suggestion) => {
    const filled = [];
    setForm((f) => {
      const next = { ...f };
      const put = (key, value, label) => {
        if (value) {
          next[key] = value;
          filled.push(label);
        }
      };
      put('name', suggestion.name, 'tên');
      put('color', suggestion.color, 'màu');
      put('colorTone', suggestion.colorTone, 'tone màu');
      put('category', suggestion.category, 'danh mục');
      put('season', suggestion.season, 'mùa');
      put('style', suggestion.style, 'phong cách');
      return next;
    });
    setAiFilled(filled);
  };

  const runAnalysis = async (file) => {
    setAnalyzing(true);
    setAiFilled(null);
    try {
      applySuggestion(await analyzeClothingImage(await shrinkImageForAi(file)));
    } catch (err) {
      // Nhận diện hỏng không được chặn việc thêm đồ — người dùng vẫn nhập tay được.
      setError(`Không nhận diện được ảnh: ${err.message}`);
    } finally {
      setAnalyzing(false);
    }
  };

  const handleFile = async (file) => {
    if (!file) return;

    setError(null);
    setUploading(true);
    lastFileRef.current = file;

    // Tải ảnh lên và nhờ AI nhận diện chạy song song — người dùng chỉ chờ một lần.
    // Chỉ tự nhận diện khi thêm đồ mới; lúc sửa thì thông tin đã có sẵn, đợi người dùng
    // bấm nút thì mới gọi để không tốn token vô ích.
    const analysis = aiReady && !item ? runAnalysis(file) : null;

    try {
      const { url } = await uploadClothingImage(file);
      setForm((f) => ({ ...f, imageUrl: url }));
    } catch (err) {
      setError(err.message);
    } finally {
      setUploading(false);
    }

    await analysis;
  };

  const handleUpload = (e) => {
    const file = e.target.files?.[0];
    e.target.value = ''; // cho phép chọn lại cùng một file
    handleFile(file);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setDragging(false);
    const file = Array.from(e.dataTransfer.files || []).find((f) => f.type.startsWith('image/'));
    if (file) {
      handleFile(file);
    } else {
      setError('Hãy thả một tệp ảnh JPG hoặc PNG.');
    }
  };

  const busyWithImage = uploading || analyzing;

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setSaving(true);
    try {
      await onSave({
        name: form.name.trim(),
        color: form.color.trim() || null,
        colorTone: form.colorTone || null,
        category: form.category,
        season: form.season,
        style: form.style,
        condition: form.condition,
        status: form.status,
        favorite: form.favorite,
        imageUrl: form.imageUrl.trim() || null,
        wearCount: item?.wearCount ?? 0,
        lastWornAt: item?.lastWornAt ?? null,
      });
    } catch (err) {
      const fieldErrors = Object.values(err.fieldErrors || {});
      setError(fieldErrors.length > 0 ? fieldErrors.join(' ') : err.message);
      setSaving(false);
    }
  };

  const renderSelect = (key, labels) => (
    <select className="nb-select" value={form[key]} onChange={set(key)}>
      {Object.entries(labels).map(([value, text]) => (
        <option key={value} value={value}>
          {text}
        </option>
      ))}
    </select>
  );

  return (
    <Modal title={item ? '✏️ Sửa món đồ' : '➕ Thêm món đồ'} onClose={onClose}>
      <ErrorBanner error={error} onDismiss={() => setError(null)} />
      <form onSubmit={handleSubmit}>
        {/* Ảnh đứng đầu form: luồng chính giờ là chụp ảnh rồi để AI điền phần còn lại. */}
        <input
          ref={fileInputRef}
          type="file"
          accept="image/jpeg,image/png"
          onChange={handleUpload}
          style={{ display: 'none' }}
        />

        <div
          className={`photo-drop ${dragging ? 'is-dragging' : ''} ${busyWithImage ? 'is-busy' : ''}`}
          onClick={() => !busyWithImage && handlePickFile()}
          onDragOver={(e) => {
            e.preventDefault();
            setDragging(true);
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={handleDrop}
          role="button"
          tabIndex={0}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              e.preventDefault();
              handlePickFile();
            }
          }}
        >
          {form.imageUrl.trim() ? (
            <img className="photo-drop-preview" src={form.imageUrl.trim()} alt="Ảnh món đồ" />
          ) : (
            <span className="photo-drop-icon" aria-hidden="true">
              {aiReady ? '✨' : '📷'}
            </span>
          )}

          <div className="photo-drop-text">
            <strong>
              {busyWithImage
                ? analyzing
                  ? '🔍 AI đang xem ảnh...'
                  : '⏳ Đang tải ảnh lên...'
                : form.imageUrl.trim()
                  ? 'Đổi ảnh khác'
                  : aiReady
                    ? 'Chọn ảnh — AI tự điền thông tin'
                    : 'Chọn ảnh minh họa'}
            </strong>
            <span>
              {aiReady
                ? 'Bấm hoặc kéo thả ảnh vào đây. AI sẽ đoán tên, màu, danh mục, mùa và phong cách.'
                : 'Bấm hoặc kéo thả ảnh JPG/PNG vào đây.'}
            </span>
          </div>
        </div>

        {aiFilled && (
          <div className={`ai-result ${aiFilled.length > 0 ? '' : 'is-empty'}`}>
            {aiFilled.length > 0
              ? `✨ AI đã điền ${aiFilled.join(', ')} — kiểm tra lại bên dưới rồi lưu.`
              : '🤔 AI không nhận ra món đồ nào trong ảnh, bạn nhập tay giúp nhé.'}
          </div>
        )}

        <div className="photo-drop-extra">
          {aiReady && lastFileRef.current && (
            <Button
              type="button"
              variant="purple"
              size="sm"
              onClick={() => runAnalysis(lastFileRef.current)}
              disabled={busyWithImage}
            >
              🔁 Cho AI đọc lại ảnh
            </Button>
          )}
          <Button type="button" size="sm" onClick={() => setShowUrlInput((v) => !v)}>
            🔗 {showUrlInput ? 'Ẩn ô URL' : 'Dán URL ảnh'}
          </Button>
          <span className="photo-drop-samples-label">hoặc chọn ảnh mẫu:</span>
          <div className="sample-pick-row">
            {SAMPLE_IMAGES.map((sample) => (
              <button
                key={sample.url}
                type="button"
                className={`sample-pick ${form.imageUrl === sample.url ? 'is-active' : ''}`}
                title={sample.label}
                onClick={() =>
                  setForm((f) => ({ ...f, imageUrl: f.imageUrl === sample.url ? '' : sample.url }))
                }
              >
                <img src={sample.url} alt={sample.label} />
              </button>
            ))}
          </div>
        </div>

        {showUrlInput && (
          <Field label="URL ảnh">
            <input
              className="nb-input"
              value={form.imageUrl}
              onChange={set('imageUrl')}
              placeholder="https://..."
              maxLength={255}
            />
          </Field>
        )}

        <Field label="Tên món đồ *">
          <input
            className="nb-input"
            value={form.name}
            onChange={set('name')}
            placeholder="vd: Áo sơ mi trắng"
            required
            maxLength={255}
          />
        </Field>

        <div className="two-col">
          <Field label="Màu sắc">
            <input className="nb-input" value={form.color} onChange={set('color')} placeholder="vd: Trắng" />
          </Field>
          <Field label="Tone màu">
            <select className="nb-select" value={form.colorTone} onChange={set('colorTone')}>
              <option value="">— Chưa phân loại —</option>
              {Object.entries(TONE_LABELS).map(([value, text]) => (
                <option key={value} value={value}>
                  {text}
                </option>
              ))}
            </select>
          </Field>
        </div>

        <div className="two-col">
          <Field label="Danh mục *">{renderSelect('category', CATEGORY_LABELS)}</Field>
          <Field label="Mùa *">{renderSelect('season', SEASON_LABELS)}</Field>
        </div>

        <div className="two-col">
          <Field label="Phong cách *">{renderSelect('style', STYLE_LABELS)}</Field>
        </div>

        {item ? (
          <div className="two-col">
            <Field label="Tình trạng *">{renderSelect('condition', CONDITION_LABELS)}</Field>
            <Field label="Trạng thái *">{renderSelect('status', STATUS_LABELS)}</Field>
          </div>
        ) : (
          <p style={{ fontSize: 13.5, fontWeight: 600, color: 'var(--muted)', marginBottom: 14 }}>
            💡 Đồ mới mặc định là <strong>Còn tốt</strong> · <strong>Sẵn sàng</strong> — có thể đổi
            sau trong phần sửa hoặc trang chi tiết.
          </p>
        )}

        <label className="nb-checkbox-row">
          <input
            type="checkbox"
            checked={form.favorite}
            onChange={(e) => setForm((f) => ({ ...f, favorite: e.target.checked }))}
          />
          ⭐ Đánh dấu yêu thích
        </label>

        <div className="nb-modal-actions">
          <Button onClick={onClose}>Hủy</Button>
          <button type="submit" className="nb-btn nb-btn--primary" disabled={saving}>
            {saving ? 'Đang lưu...' : '💾 Lưu'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
