import { useRef, useState } from 'react';
import { analyzeClothingImageBatch } from '../api/ai';
import * as itemsApi from '../api/clothingItems';
import { shrinkImageForAi } from '../utils/image';
import { Button, ErrorBanner, Modal } from './ui';
import { CATEGORY_LABELS, SEASON_LABELS, STYLE_LABELS } from '../utils/labels';

/**
 * Chụp một ảnh chứa nhiều món → AI liệt kê từng món → người dùng bỏ tick / sửa nhanh → thêm cả lô.
 *
 * Bắt buộc có bước soát lại: ảnh nhiều món chồng lên nhau thì AI dễ nhận nhầm hoặc bỏ sót,
 * lưu thẳng sẽ đổ rác vào tủ đồ và người dùng phải đi dọn từng món.
 */
export default function BulkScanModal({ onClose, onDone }) {
  const [rows, setRows] = useState(null);
  const [error, setError] = useState(null);
  const [scanning, setScanning] = useState(false);
  const [saving, setSaving] = useState(false);
  const [dragging, setDragging] = useState(false);
  const fileInputRef = useRef(null);

  const scan = async (file) => {
    if (!file) return;
    setError(null);
    setScanning(true);
    try {
      const suggestions = await analyzeClothingImageBatch(await shrinkImageForAi(file));
      setRows(
        suggestions.map((s, index) => ({
          key: `${index}-${s.name ?? ''}`,
          picked: true,
          name: s.name ?? '',
          color: s.color ?? '',
          colorTone: s.colorTone ?? '',
          category: s.category ?? 'SHIRT',
          season: s.season ?? 'ALL_SEASON',
          style: s.style ?? 'CASUAL',
        }))
      );
    } catch (err) {
      setError(err.message);
    } finally {
      setScanning(false);
    }
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setDragging(false);
    const file = Array.from(e.dataTransfer.files || []).find((f) => f.type.startsWith('image/'));
    if (file) scan(file);
    else setError('Hãy thả một tệp ảnh JPG hoặc PNG.');
  };

  const update = (key, field) => (e) =>
    setRows((list) => list.map((r) => (r.key === key ? { ...r, [field]: e.target.value } : r)));

  const togglePick = (key) =>
    setRows((list) => list.map((r) => (r.key === key ? { ...r, picked: !r.picked } : r)));

  const picked = rows?.filter((r) => r.picked && r.name.trim()) ?? [];

  const handleSave = async () => {
    setSaving(true);
    setError(null);
    try {
      const created = await itemsApi.createItems(
        picked.map((r) => ({
          name: r.name.trim(),
          color: r.color.trim() || null,
          colorTone: r.colorTone || null,
          category: r.category,
          season: r.season,
          style: r.style,
          condition: 'GOOD',
          status: 'AVAILABLE',
          favorite: false,
          imageUrl: null,
        }))
      );
      onDone(created.length);
    } catch (err) {
      setError(err.message);
      setSaving(false);
    }
  };

  const select = (row, field, labels) => (
    <select className="nb-select bulk-select" value={row[field]} onChange={update(row.key, field)}>
      {Object.entries(labels).map(([value, text]) => (
        <option key={value} value={value}>
          {text}
        </option>
      ))}
    </select>
  );

  return (
    <Modal title="📸 Quét nhiều món từ một ảnh" onClose={onClose} wide>
      <ErrorBanner error={error} onDismiss={() => setError(null)} />

      {rows === null ? (
        <>
          <input
            ref={fileInputRef}
            type="file"
            accept="image/jpeg,image/png"
            onChange={(e) => {
              const file = e.target.files?.[0];
              e.target.value = '';
              scan(file);
            }}
            style={{ display: 'none' }}
          />
          <div
            className={`photo-drop ${dragging ? 'is-dragging' : ''} ${scanning ? 'is-busy' : ''}`}
            onClick={() => !scanning && fileInputRef.current?.click()}
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
                fileInputRef.current?.click();
              }
            }}
          >
            <span className="photo-drop-icon" aria-hidden="true">
              {scanning ? '⏳' : '📸'}
            </span>
            <div className="photo-drop-text">
              <strong>{scanning ? 'AI đang đếm từng món...' : 'Chọn ảnh chứa nhiều món'}</strong>
              <span>
                Chụp cả kệ tủ hoặc trải đồ ra giường rồi chụp một tấm. AI nhận tối đa 12 món mỗi
                ảnh — bày càng thưa, nhận càng chuẩn.
              </span>
            </div>
          </div>
        </>
      ) : rows.length === 0 ? (
        <>
          <p style={{ fontWeight: 700, marginBottom: 14 }}>
            🤔 AI không nhận ra món quần áo nào trong ảnh. Thử chụp gần hơn, đủ sáng và tránh để đồ
            chồng lên nhau.
          </p>
          <div className="nb-modal-actions">
            <Button onClick={() => setRows(null)}>← Chọn ảnh khác</Button>
          </div>
        </>
      ) : (
        <>
          <p style={{ fontWeight: 600, fontSize: 13.5, color: 'var(--muted)', marginBottom: 12 }}>
            AI nhận ra <strong>{rows.length} món</strong>. Bỏ tick món nhận nhầm, sửa lại tên nếu
            cần, rồi bấm thêm. Ảnh minh họa có thể bổ sung sau ở từng món.
          </p>

          <div className="bulk-list">
            {rows.map((row) => (
              <div key={row.key} className={`bulk-row ${row.picked ? '' : 'is-skipped'}`}>
                <label className="bulk-pick">
                  <input type="checkbox" checked={row.picked} onChange={() => togglePick(row.key)} />
                </label>
                <div className="bulk-fields">
                  <input
                    className="nb-input bulk-name"
                    value={row.name}
                    onChange={update(row.key, 'name')}
                    placeholder="Tên món đồ"
                    maxLength={255}
                  />
                  <input
                    className="nb-input bulk-color"
                    value={row.color}
                    onChange={update(row.key, 'color')}
                    placeholder="Màu"
                  />
                  {select(row, 'category', CATEGORY_LABELS)}
                  {select(row, 'season', SEASON_LABELS)}
                  {select(row, 'style', STYLE_LABELS)}
                </div>
              </div>
            ))}
          </div>

          <div className="nb-modal-actions">
            <Button onClick={() => setRows(null)} disabled={saving}>
              ← Ảnh khác
            </Button>
            <button
              type="button"
              className="nb-btn nb-btn--primary"
              onClick={handleSave}
              disabled={saving || picked.length === 0}
            >
              {saving ? 'Đang thêm...' : `💾 Thêm ${picked.length} món`}
            </button>
          </div>
        </>
      )}
    </Modal>
  );
}
