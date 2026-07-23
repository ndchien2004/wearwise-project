import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import * as outfitsApi from '../api/outfits';
import * as tryOnApi from '../api/tryOn';
import OutfitFormModal from '../components/OutfitFormModal';
import { Badge, Button, EmptyState, ErrorBanner, Loading } from '../components/ui';
import { useConfirm } from '../context/ConfirmContext';
import {
  CATEGORY_COLORS,
  CATEGORY_EMOJIS,
  CATEGORY_LABELS,
  SEASON_EMOJIS,
  SEASON_LABELS,
  STYLE_LABELS,
  label,
} from '../utils/labels';
import { daysSince, formatDateTime, isWornToday } from '../utils/date';

export default function OutfitDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const confirm = useConfirm();

  const [outfit, setOutfit] = useState(null);
  const [error, setError] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [tryOnHistory, setTryOnHistory] = useState([]);
  const [tryOnError, setTryOnError] = useState(null);
  const [tryingOn, setTryingOn] = useState(false);

  const load = useCallback(async () => {
    try {
      setOutfit(await outfitsApi.getOutfit(id));
    } catch (err) {
      if (err.status === 404) setNotFound(true);
      else setError(err.message);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  // Lịch sử ảnh đã thử của riêng outfit này (không chặn trang nếu lỗi).
  useEffect(() => {
    tryOnApi.listTryOnsForOutfit(id).then(setTryOnHistory).catch(() => setTryOnHistory([]));
  }, [id]);

  const handleWear = async () => {
    setBusy(true);
    setError(null);
    try {
      setOutfit(await outfitsApi.markOutfitWorn(outfit.id));
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const handleToggleFavorite = async () => {
    setBusy(true);
    setError(null);
    try {
      setOutfit(await outfitsApi.setOutfitFavorite(outfit.id, !outfit.favorite));
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const handleTryOn = async () => {
    setTryOnError(null);
    setTryingOn(true);
    try {
      const result = await tryOnApi.generateOutfitTryOn(outfit.id);
      setTryOnHistory((list) => [result, ...list]);
    } catch (err) {
      setTryOnError(err.message);
    } finally {
      setTryingOn(false);
    }
  };

  const handleDeleteTryOn = async (result) => {
    const ok = await confirm({
      title: 'Xóa ảnh thử đồ?',
      message: 'Ảnh thử đồ này sẽ bị xóa khỏi lịch sử của outfit.',
      confirmLabel: '🗑️ Xóa luôn',
      danger: true,
    });
    if (!ok) return;
    try {
      await tryOnApi.deleteTryOn(result.id);
      setTryOnHistory((list) => list.filter((r) => r.id !== result.id));
    } catch (err) {
      setTryOnError(err.message);
    }
  };

  const handleDelete = async () => {
    const ok = await confirm({
      title: 'Xóa outfit?',
      message: `"${outfit.name}" sẽ bị xóa, kèm theo các kế hoạch trong lịch đang dùng outfit này.`,
      confirmLabel: '🗑️ Xóa luôn',
      danger: true,
    });
    if (!ok) return;
    setBusy(true);
    try {
      await outfitsApi.deleteOutfit(outfit.id);
      navigate('/outfits');
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  };

  const handleEditSave = async (payload) => {
    const updated = await outfitsApi.updateOutfit(outfit.id, payload);
    setOutfit(updated);
    setEditing(false);
  };

  if (notFound) {
    return (
      <div>
        <EmptyState emoji="🔍">Không tìm thấy outfit này. Có thể nó đã bị xóa.</EmptyState>
        <div style={{ marginTop: 16 }}>
          <Link to="/outfits" className="nb-btn">← Về danh sách outfit</Link>
        </div>
      </div>
    );
  }

  if (!outfit) {
    return (
      <div>
        <ErrorBanner error={error} onDismiss={() => setError(null)} />
        <Loading>Đang mở outfit...</Loading>
      </div>
    );
  }

  const wornToday = isWornToday(outfit.lastWornAt);
  const days = daysSince(outfit.lastWornAt);
  const collageImages = outfit.clothingItems.filter((item) => item.imageUrl).slice(0, 4);

  return (
    <div>
      <div className="page-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: 14, flexWrap: 'wrap' }}>
          <Link to="/outfits" className="nb-btn nb-btn--sm">← Outfit</Link>
          <h1 className="page-title">🧢 {outfit.name}</h1>
        </div>
        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
          <Button onClick={() => setEditing(true)}>✏️ Sửa</Button>
          <Button variant="danger" onClick={handleDelete} disabled={busy}>🗑️ Xóa</Button>
        </div>
      </div>

      <ErrorBanner error={error} onDismiss={() => setError(null)} />

      <div className="two-col">
        <div>
          <div className="nb-card" style={{ marginBottom: 20 }}>
            {collageImages.length > 0 ? (
              <div
                className="outfit-collage"
                style={collageImages.length === 1 ? { gridTemplateColumns: '1fr' } : undefined}
              >
                {collageImages.map((item) => (
                  <div key={item.id} className="collage-cell" title={item.name}>
                    <img src={item.imageUrl} alt={item.name} />
                  </div>
                ))}
              </div>
            ) : (
              <div className="item-photo item-photo--placeholder" style={{ background: 'var(--pink)' }}>
                🧢
              </div>
            )}

            <div className="badge-row" style={{ marginTop: 14 }}>
              <Badge color="blue">
                {SEASON_EMOJIS[outfit.season]} {label(SEASON_LABELS, outfit.season)}
              </Badge>
              <Badge color="purple">{label(STYLE_LABELS, outfit.style)}</Badge>
              {outfit.favorite && <Badge color="pink">⭐ Yêu thích</Badge>}
            </div>

            {outfit.description && (
              <p style={{ fontWeight: 600, color: 'var(--muted)', fontSize: 13.5, marginTop: 12, marginBottom: 0 }}>
                {outfit.description}
              </p>
            )}

            <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginTop: 16 }}>
              {wornToday ? (
                <Button disabled>✅ Đã mặc hôm nay</Button>
              ) : (
                <Button variant="green" onClick={handleWear} disabled={busy}>
                  👣 Mặc hôm nay
                </Button>
              )}
              <Button variant={outfit.favorite ? 'pink' : undefined} onClick={handleToggleFavorite} disabled={busy}>
                {outfit.favorite ? '💔 Bỏ yêu thích' : '⭐ Yêu thích'}
              </Button>
            </div>
          </div>

          <div className="nb-card">
            <h3 className="chart-title">👕 Các món trong bộ ({outfit.clothingItems.length})</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {outfit.clothingItems.map((item) => (
                <Link
                  key={item.id}
                  to={`/wardrobe/${item.id}`}
                  className="outfit-item-row"
                  title={item.name}
                >
                  {item.imageUrl ? (
                    <img className="outfit-item-thumb" src={item.imageUrl} alt={item.name} />
                  ) : (
                    <span
                      className="outfit-item-thumb outfit-item-thumb--emoji"
                      style={{ background: CATEGORY_COLORS[item.category] || 'var(--yellow)' }}
                    >
                      {CATEGORY_EMOJIS[item.category] || '👗'}
                    </span>
                  )}
                  <span className="outfit-item-row-name">{item.name}</span>
                  <Badge color="yellow">{label(CATEGORY_LABELS, item.category)}</Badge>
                </Link>
              ))}
            </div>
          </div>
        </div>

        <div>
          <div className="nb-card" style={{ marginBottom: 20 }}>
            <h3 className="chart-title">🪞 Thử cả bộ lên người bạn</h3>
            {collageImages.length > 0 ? (
              <>
                <p style={{ fontWeight: 600, color: 'var(--muted)', fontSize: 13.5, marginBottom: 12 }}>
                  Ghép {collageImages.length > 1 ? 'các món có ảnh' : 'món có ảnh'} trong bộ này lên ảnh của bạn.
                </p>
                <Button variant="pink" onClick={handleTryOn} disabled={tryingOn}>
                  {tryingOn ? '⏳ Đang ghép (1–2 phút)...' : '🪞 Thử nguyên bộ'}
                </Button>
              </>
            ) : (
              <p style={{ fontWeight: 600, color: 'var(--muted)', fontSize: 13.5 }}>
                Các món trong bộ chưa có ảnh minh họa. Hãy thêm ảnh cho từng món để thử cả bộ.
              </p>
            )}

            {tryOnError && (
              <div style={{ marginTop: 12 }}>
                <ErrorBanner error={tryOnError} onDismiss={() => setTryOnError(null)} />
                <Link to="/try-on" className="nb-btn nb-btn--sm">
                  → Tới trang Thử đồ ảo
                </Link>
              </div>
            )}

            {tryOnHistory.length > 0 && (
              <div style={{ marginTop: 16 }}>
                <p style={{ fontWeight: 700, fontSize: 13.5, marginBottom: 10 }}>
                  👗 Đã thử ({tryOnHistory.length})
                </p>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
                  {tryOnHistory.map((result) => (
                    <div key={result.id}>
                      <div className="tryon-result-photo">
                        <img src={result.resultImageUrl} alt="Kết quả thử đồ" />
                      </div>
                      <div
                        style={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                          gap: 8,
                          marginTop: 6,
                          flexWrap: 'wrap',
                        }}
                      >
                        <Badge color="purple">🕐 {formatDateTime(result.createdAt)}</Badge>
                        <div style={{ display: 'flex', gap: 8 }}>
                          <a
                            className="nb-btn nb-btn--sm"
                            href={result.resultImageUrl}
                            target="_blank"
                            rel="noreferrer"
                          >
                            🔍 Ảnh gốc
                          </a>
                          <Button size="sm" variant="danger" onClick={() => handleDeleteTryOn(result)}>
                            🗑️
                          </Button>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>

          <div className="nb-card">
            <h3 className="chart-title">📈 Lịch sử mặc</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8, fontWeight: 600 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10 }}>
                <span>Tổng lượt mặc</span>
                <Badge color="orange">{outfit.wearCount ?? 0} lần</Badge>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10 }}>
                <span>Mặc lần cuối</span>
                <span style={{ color: 'var(--muted)' }}>
                  {formatDateTime(outfit.lastWornAt)}
                  {days !== null && days > 0 ? ` (${days} ngày trước)` : ''}
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10 }}>
                <span>Tạo lúc</span>
                <span style={{ color: 'var(--muted)' }}>{formatDateTime(outfit.createdAt)}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {editing && (
        <OutfitFormModal outfit={outfit} onSave={handleEditSave} onClose={() => setEditing(false)} />
      )}
    </div>
  );
}
