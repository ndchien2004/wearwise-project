import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import * as itemsApi from '../api/clothingItems';
import * as tryOnApi from '../api/tryOn';
import { Badge, Button, EmptyState, ErrorBanner, Loading } from '../components/ui';
import { useConfirm } from '../context/ConfirmContext';
import { CATEGORY_EMOJIS } from '../utils/labels';
import { formatDateTime } from '../utils/date';

const GUIDELINES = [
  'Ảnh rõ nét, đủ sáng, không bị mờ hay nhòe.',
  'Chụp thẳng, thấy rõ toàn thân hoặc ít nhất nửa thân trên.',
  'Chỉ có một người trong ảnh, tư thế đứng tự nhiên.',
  'Nền đơn giản, không bị che khuất cơ thể.',
  'Định dạng JPG hoặc PNG, tối đa 10MB, mỗi cạnh ≥ 300px.',
];

export default function TryOnPage() {
  const confirm = useConfirm();
  const fileInputRef = useRef(null);

  const [profile, setProfile] = useState(null);
  const [items, setItems] = useState([]);
  const [results, setResults] = useState([]);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [generatingId, setGeneratingId] = useState(null);

  useEffect(() => {
    tryOnApi.getTryOnProfile().then(setProfile).catch((err) => setError(err.message));
    itemsApi.findItems().then(setItems).catch(() => setItems([]));
    tryOnApi.listTryOns().then(setResults).catch(() => setResults([]));
  }, []);

  const wearableItems = items.filter((item) => item.imageUrl);

  const handlePickFile = () => fileInputRef.current?.click();

  const handleFileChange = async (e) => {
    const file = e.target.files?.[0];
    e.target.value = ''; // cho phép chọn lại cùng một file
    if (!file) return;

    setError(null);
    setNotice(null);
    setUploading(true);
    try {
      const updated = await tryOnApi.uploadBodyPhoto(file);
      setProfile(updated);
      setNotice('Đã lưu ảnh của bạn! Giờ hãy chọn một món đồ để thử. 🪞');
    } catch (err) {
      setError(err.message);
    } finally {
      setUploading(false);
    }
  };

  const handleRemovePhoto = async () => {
    const ok = await confirm({
      title: 'Xóa ảnh của bạn?',
      message: 'Ảnh dùng để thử đồ sẽ bị gỡ. Bạn có thể tải ảnh mới bất cứ lúc nào.',
      confirmLabel: '🗑️ Xóa ảnh',
      danger: true,
    });
    if (!ok) return;
    setError(null);
    try {
      setProfile(await tryOnApi.deleteBodyPhoto());
    } catch (err) {
      setError(err.message);
    }
  };

  const handleTryOn = async (item) => {
    setError(null);
    setNotice(null);
    setGeneratingId(item.id);
    try {
      const result = await tryOnApi.generateTryOn(item.id);
      setResults((list) => [result, ...list]);
      setNotice(`Đã ghép "${item.name}" lên ảnh của bạn! 🎉`);
    } catch (err) {
      setError(err.message);
    } finally {
      setGeneratingId(null);
    }
  };

  const handleDeleteResult = async (result) => {
    const ok = await confirm({
      title: 'Xóa ảnh thử đồ?',
      message: 'Ảnh thử đồ này sẽ bị xóa khỏi bộ sưu tập.',
      confirmLabel: '🗑️ Xóa luôn',
      danger: true,
    });
    if (!ok) return;
    try {
      await tryOnApi.deleteTryOn(result.id);
      setResults((list) => list.filter((r) => r.id !== result.id));
    } catch (err) {
      setError(err.message);
    }
  };

  if (!profile) {
    return (
      <div>
        <ErrorBanner error={error} onDismiss={() => setError(null)} />
        <Loading>Đang mở phòng thử đồ...</Loading>
      </div>
    );
  }

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">🪞 Thử đồ ảo</h1>
      </div>

      <input
        ref={fileInputRef}
        type="file"
        accept="image/jpeg,image/png"
        onChange={handleFileChange}
        style={{ display: 'none' }}
      />

      <ErrorBanner error={error} onDismiss={() => setError(null)} />
      {notice && (
        <div className="error-banner" style={{ background: 'var(--green)' }}>
          {notice}
          <button
            type="button"
            onClick={() => setNotice(null)}
            style={{ float: 'right', background: 'none', border: 'none', cursor: 'pointer', fontWeight: 900 }}
          >
            ✕
          </button>
        </div>
      )}

      <div className="two-col" style={{ marginBottom: 28 }}>
        {/* --- Ảnh của bạn --- */}
        <div className="nb-card">
          <h3 className="chart-title">📸 Ảnh của bạn</h3>
          {profile.hasBodyPhoto ? (
            <>
              <div className="tryon-photo">
                <img src={profile.bodyPhotoUrl} alt="Ảnh của bạn" />
              </div>
              <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginTop: 14 }}>
                <Button variant="primary" onClick={handlePickFile} disabled={uploading}>
                  {uploading ? '⏳ Đang tải...' : '🔄 Đổi ảnh khác'}
                </Button>
                <Button variant="danger" onClick={handleRemovePhoto} disabled={uploading}>
                  🗑️ Xóa ảnh
                </Button>
              </div>
            </>
          ) : (
            <>
              <button
                type="button"
                className="tryon-dropzone"
                onClick={handlePickFile}
                disabled={uploading}
              >
                <span className="tryon-dropzone-emoji">{uploading ? '⏳' : '⬆️'}</span>
                <strong>{uploading ? 'Đang tải ảnh lên...' : 'Bấm để tải ảnh của bạn lên'}</strong>
                <span style={{ color: 'var(--muted)', fontWeight: 600, fontSize: 13.5 }}>
                  Chỉ cần làm một lần. JPG/PNG, tối đa 10MB.
                </span>
              </button>
            </>
          )}

          <div className="tryon-guidelines">
            <strong>✅ Ảnh đạt yêu cầu khi:</strong>
            <ul>
              {GUIDELINES.map((g) => (
                <li key={g}>{g}</li>
              ))}
            </ul>
          </div>
        </div>

        {/* --- Chọn đồ để thử --- */}
        <div className="nb-card">
          <h3 className="chart-title">👗 Chọn món đồ để thử</h3>
          {!profile.hasBodyPhoto ? (
            <EmptyState emoji="📸">Hãy tải ảnh của bạn lên trước, rồi chọn đồ để thử nhé!</EmptyState>
          ) : wearableItems.length === 0 ? (
            <EmptyState emoji="🖼️">
              Chưa có món đồ nào có ảnh minh họa.{' '}
              <Link to="/wardrobe" style={{ fontWeight: 700 }}>
                Thêm ảnh cho đồ trong tủ
              </Link>{' '}
              rồi quay lại thử nhé.
            </EmptyState>
          ) : (
            <div className="tryon-item-grid">
              {wearableItems.map((item) => (
                <div key={item.id} className="tryon-item">
                  <div className="tryon-item-photo">
                    <img src={item.imageUrl} alt={item.name} />
                  </div>
                  <div className="tryon-item-name" title={item.name}>
                    {CATEGORY_EMOJIS[item.category]} {item.name}
                  </div>
                  <Button
                    size="sm"
                    variant="green"
                    onClick={() => handleTryOn(item)}
                    disabled={generatingId !== null}
                  >
                    {generatingId === item.id ? '⏳ Đang ghép...' : '✨ Thử lên người'}
                  </Button>
                </div>
              ))}
            </div>
          )}
          {generatingId !== null && (
            <p style={{ fontWeight: 600, color: 'var(--muted)', marginTop: 12, fontSize: 13.5 }}>
              ⏳ Kling AI đang ghép trang phục — có thể mất tới 1–2 phút, vui lòng đợi...
            </p>
          )}
        </div>
      </div>

      {/* --- Bộ sưu tập --- */}
      <h2 className="section-heading">🖼️ Bộ sưu tập thử đồ</h2>
      {results.length === 0 ? (
        <EmptyState emoji="✨">Chưa có ảnh thử đồ nào. Chọn một món đồ ở trên để bắt đầu!</EmptyState>
      ) : (
        <div className="card-grid">
          {results.map((result) => (
            <div key={result.id} className="nb-card item-card">
              <div className="tryon-result-photo">
                <img src={result.resultImageUrl} alt={result.clothingItemName || 'Ảnh thử đồ'} />
              </div>
              <div className="item-name">{result.clothingItemName || 'Món đồ đã xóa'}</div>
              <div className="badge-row">
                <Badge color="purple">🕐 {formatDateTime(result.createdAt)}</Badge>
              </div>
              <div className="card-actions">
                <a className="nb-btn nb-btn--sm" href={result.resultImageUrl} target="_blank" rel="noreferrer">
                  🔍 Xem ảnh gốc
                </a>
                <Button size="sm" variant="danger" onClick={() => handleDeleteResult(result)}>
                  🗑️ Xóa
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
