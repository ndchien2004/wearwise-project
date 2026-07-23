import { useRef, useState } from 'react';
import { Button, ErrorBanner, Field, Modal } from './ui';
import { uploadClothingImage } from '../api/images';
import {
  CATEGORY_LABELS,
  CONDITION_LABELS,
  SEASON_LABELS,
  STATUS_LABELS,
  STYLE_LABELS,
  TONE_EMOJIS,
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
  const fileInputRef = useRef(null);

  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }));

  const handlePickFile = () => fileInputRef.current?.click();

  const handleUpload = async (e) => {
    const file = e.target.files?.[0];
    e.target.value = ''; // cho phép chọn lại cùng một file
    if (!file) return;

    setError(null);
    setUploading(true);
    try {
      const { url } = await uploadClothingImage(file);
      setForm((f) => ({ ...f, imageUrl: url }));
    } catch (err) {
      setError(err.message);
    } finally {
      setUploading(false);
    }
  };

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
                  {TONE_EMOJIS[value]} {text}
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

        <Field label="Ảnh minh họa">
          <input
            ref={fileInputRef}
            type="file"
            accept="image/jpeg,image/png"
            onChange={handleUpload}
            style={{ display: 'none' }}
          />
          <div style={{ display: 'flex', gap: 8, alignItems: 'stretch' }}>
            <input
              className="nb-input"
              style={{ flex: 1 }}
              value={form.imageUrl}
              onChange={set('imageUrl')}
              placeholder="Tải ảnh lên, dán URL, hoặc chọn ảnh mẫu bên dưới"
              maxLength={255}
            />
            <Button type="button" onClick={handlePickFile} disabled={uploading}>
              {uploading ? '⏳ Đang tải...' : '⬆️ Tải ảnh'}
            </Button>
          </div>
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
          {form.imageUrl.trim() && (
            <div className="img-preview">
              <img src={form.imageUrl.trim()} alt="Xem trước" />
            </div>
          )}
        </Field>

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
