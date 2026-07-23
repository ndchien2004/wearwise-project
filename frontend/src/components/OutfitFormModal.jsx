import { useEffect, useMemo, useRef, useState } from 'react';
import * as itemsApi from '../api/clothingItems';
import { uploadOutfitImage } from '../api/images';
import { Button, ErrorBanner, Field, Modal } from './ui';
import {
  CATEGORY_EMOJIS,
  CATEGORY_LABELS,
  SEASON_LABELS,
  STYLE_LABELS,
  label,
} from '../utils/labels';

export default function OutfitFormModal({ outfit, onSave, onClose }) {
  const [form, setForm] = useState(() => ({
    name: outfit?.name ?? '',
    description: outfit?.description ?? '',
    season: outfit?.season ?? 'ALL_SEASON',
    style: outfit?.style ?? 'CASUAL',
    favorite: Boolean(outfit?.favorite),
    imageUrl: outfit?.imageUrl ?? '',
  }));
  const [pickedIds, setPickedIds] = useState(() => new Set(outfit?.clothingItems?.map((i) => i.id) ?? []));
  const [allItems, setAllItems] = useState(null);
  const [pickerFilter, setPickerFilter] = useState('');
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const fileInputRef = useRef(null);

  const handlePickFile = () => fileInputRef.current?.click();

  const handleUpload = async (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setError(null);
    setUploading(true);
    try {
      const { url } = await uploadOutfitImage(file);
      setForm((f) => ({ ...f, imageUrl: url }));
    } catch (err) {
      setError(err.message);
    } finally {
      setUploading(false);
    }
  };

  useEffect(() => {
    itemsApi
      .findItems()
      .then(setAllItems)
      .catch((err) => setError(err.message));
  }, []);

  const filteredItems = useMemo(() => {
    if (!allItems) return [];
    const q = pickerFilter.trim().toLowerCase();
    if (!q) return allItems;
    return allItems.filter(
      (i) =>
        i.name.toLowerCase().includes(q) ||
        (CATEGORY_LABELS[i.category] || '').toLowerCase().includes(q)
    );
  }, [allItems, pickerFilter]);

  // Mỗi loại chỉ 1 món: chọn món mới cùng loại sẽ thay món đang chọn.
  const togglePick = (item) => {
    setPickedIds((prev) => {
      const next = new Set(prev);
      if (next.has(item.id)) {
        next.delete(item.id);
        return next;
      }
      (allItems ?? []).forEach((other) => {
        if (other.category === item.category && next.has(other.id)) {
          next.delete(other.id);
        }
      });
      next.add(item.id);
      return next;
    });
  };

  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);

    if (pickedIds.size === 0) {
      setError('Chọn ít nhất một món đồ cho outfit.');
      return;
    }

    setSaving(true);
    try {
      await onSave({
        name: form.name.trim(),
        description: form.description.trim() || null,
        imageUrl: form.imageUrl.trim() || null,
        season: form.season,
        style: form.style,
        favorite: form.favorite,
        clothingItemIds: [...pickedIds],
      });
    } catch (err) {
      const fieldErrors = Object.values(err.fieldErrors || {});
      setError(fieldErrors.length > 0 ? fieldErrors.join(' ') : err.message);
      setSaving(false);
    }
  };

  return (
    <Modal title={outfit ? '✏️ Sửa outfit' : '➕ Tạo outfit'} wide onClose={onClose}>
      <ErrorBanner error={error} onDismiss={() => setError(null)} />
      <form onSubmit={handleSubmit}>
        <Field label="Tên outfit *">
          <input
            className="nb-input"
            value={form.name}
            onChange={set('name')}
            placeholder="vd: Đi làm thứ Hai"
            required
            maxLength={255}
          />
        </Field>

        <Field label="Mô tả">
          <textarea
            className="nb-textarea"
            value={form.description}
            onChange={set('description')}
            placeholder="Ghi chú về outfit này..."
            maxLength={1000}
          />
        </Field>

        <Field label="Ảnh đại diện outfit">
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
              placeholder="Tải ảnh chụp cả bộ, hoặc dán URL"
              maxLength={512}
            />
            <Button type="button" onClick={handlePickFile} disabled={uploading}>
              {uploading ? '⏳ Đang tải...' : '⬆️ Tải ảnh'}
            </Button>
          </div>
          {form.imageUrl.trim() && (
            <div className="img-preview">
              <img src={form.imageUrl.trim()} alt="Xem trước outfit" />
            </div>
          )}
        </Field>

        <div className="two-col">
          <Field label="Mùa *">
            <select className="nb-select" value={form.season} onChange={set('season')}>
              {Object.entries(SEASON_LABELS).map(([value, text]) => (
                <option key={value} value={value}>
                  {text}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Phong cách *">
            <select className="nb-select" value={form.style} onChange={set('style')}>
              {Object.entries(STYLE_LABELS).map(([value, text]) => (
                <option key={value} value={value}>
                  {text}
                </option>
              ))}
            </select>
          </Field>
        </div>

        <Field label={`Chọn món đồ * (đã chọn ${pickedIds.size})`}>
          <p style={{ fontWeight: 600, color: 'var(--muted)', fontSize: 13, margin: '0 0 8px' }}>
            💡 Mỗi loại chỉ chọn 1 món (1 áo, 1 quần, 1 giày...). Chọn món cùng loại sẽ thay món đang chọn.
          </p>
          <input
            className="nb-input"
            value={pickerFilter}
            onChange={(e) => setPickerFilter(e.target.value)}
            placeholder="Lọc nhanh theo tên..."
            style={{ marginBottom: 8 }}
          />
          <div className="picker-list">
            {allItems === null ? (
              <div style={{ fontWeight: 600, padding: 8 }}>Đang tải danh sách đồ...</div>
            ) : filteredItems.length === 0 ? (
              <div style={{ fontWeight: 600, padding: 8 }}>
                {allItems.length === 0 ? 'Tủ đồ trống — hãy thêm món đồ trước.' : 'Không tìm thấy món đồ nào.'}
              </div>
            ) : (
              filteredItems.map((item) => (
                <div
                  key={item.id}
                  className={`picker-row ${pickedIds.has(item.id) ? 'is-picked' : ''}`}
                  onClick={() => togglePick(item)}
                >
                  <span>{pickedIds.has(item.id) ? '✅' : '⬜'}</span>
                  {item.imageUrl ? (
                    <img className="picker-thumb" src={item.imageUrl} alt="" />
                  ) : (
                    <span>{CATEGORY_EMOJIS[item.category]}</span>
                  )}
                  <span style={{ flex: 1 }}>{item.name}</span>
                  <span style={{ color: 'var(--muted)', fontSize: 12.5 }}>
                    {label(CATEGORY_LABELS, item.category)}
                  </span>
                </div>
              ))
            )}
          </div>
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
            {saving ? 'Đang lưu...' : '💾 Lưu outfit'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
