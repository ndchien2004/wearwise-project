import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import * as itemsApi from '../api/clothingItems';
import * as outfitsApi from '../api/outfits';
import * as tryOnApi from '../api/tryOn';
import ItemFormModal from '../components/ItemFormModal';
import ShareModal from '../components/ShareModal';
import { Badge, Button, EmptyState, ErrorBanner, Loading, Toast } from '../components/ui';
import { useConfirm } from '../context/ConfirmContext';
import {
  BLOCK_REASON_EMOJIS,
  BLOCK_REASON_LABELS,
  CATEGORY_COLORS,
  CATEGORY_EMOJIS,
  CATEGORY_LABELS,
  CONDITION_LABELS,
  SEASON_EMOJIS,
  SEASON_LABELS,
  STATUS_LABELS,
  STYLE_LABELS,
  TONE_BADGE_COLORS,
  TONE_EMOJIS,
  TONE_LABELS,
  label,
} from '../utils/labels';
import { daysSince, formatDateTime, isWornToday } from '../utils/date';

const STATUS_OPTIONS = [
  { value: 'AVAILABLE', text: '✅ Sẵn sàng', variant: 'green' },
  { value: 'LAUNDRY', text: '🫧 Đang giặt', variant: 'blue' },
  { value: 'UNAVAILABLE', text: '🚫 Chưa dùng được', variant: undefined },
];

const CONDITION_OPTIONS = [
  { value: 'GOOD', text: '👍 Còn tốt', variant: 'green' },
  { value: 'DAMAGED', text: '🩹 Hư hỏng', variant: 'danger' },
];

export default function ItemDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const confirm = useConfirm();

  const [item, setItem] = useState(null);
  const [relatedOutfits, setRelatedOutfits] = useState([]);
  const [error, setError] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [editing, setEditing] = useState(false);
  const [sharing, setSharing] = useState(false);
  const [toast, setToast] = useState(null);
  const [busy, setBusy] = useState(false);
  const [tryOnHistory, setTryOnHistory] = useState([]);
  const [tryOnError, setTryOnError] = useState(null);
  const [tryingOn, setTryingOn] = useState(false);

  const load = useCallback(async () => {
    try {
      const [loadedItem, outfits] = await Promise.all([itemsApi.getItem(id), outfitsApi.findOutfits()]);
      setItem(loadedItem);
      setRelatedOutfits(outfits.filter((o) => o.clothingItems.some((i) => i.id === loadedItem.id)));
    } catch (err) {
      if (err.status === 404) setNotFound(true);
      else setError(err.message);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  // Lịch sử ảnh đã thử của riêng món này (không chặn trang nếu lỗi).
  useEffect(() => {
    tryOnApi.listTryOnsForItem(id).then(setTryOnHistory).catch(() => setTryOnHistory([]));
  }, [id]);

  // PUT yêu cầu đầy đủ payload nên gửi lại các trường hiện có, chỉ đổi phần cần đổi.
  const patchItem = async (changes) => {
    setBusy(true);
    setError(null);
    try {
      const updated = await itemsApi.updateItem(item.id, {
        name: item.name,
        color: item.color,
        colorTone: item.colorTone,
        category: item.category,
        season: item.season,
        style: item.style,
        condition: item.condition,
        status: item.status,
        wearCount: item.wearCount,
        lastWornAt: item.lastWornAt,
        favorite: item.favorite,
        imageUrl: item.imageUrl,
        ...changes,
      });
      setItem(updated);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const handleWear = async () => {
    setBusy(true);
    setError(null);
    try {
      setItem(await itemsApi.markItemWorn(item.id));
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const handleWashed = () => patchItem({ status: 'AVAILABLE' });

  const handleToggleFavorite = async () => {
    setBusy(true);
    setError(null);
    try {
      setItem(await itemsApi.setItemFavorite(item.id, !item.favorite));
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
      const result = await tryOnApi.generateTryOn(item.id);
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
      message: 'Ảnh thử đồ này sẽ bị xóa khỏi lịch sử của món đồ.',
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
      title: 'Xóa món đồ?',
      message: `"${item.name}" sẽ bị xóa khỏi tủ đồ. Hành động này không thể hoàn tác.`,
      confirmLabel: '🗑️ Xóa luôn',
      danger: true,
    });
    if (!ok) return;
    setBusy(true);
    try {
      await itemsApi.deleteItem(item.id);
      navigate('/wardrobe');
      return;
    } catch (err) {
      setBusy(false);
      if (err.code !== 'CLOTHING_ITEM_IN_USE') {
        setError(err.message);
        return;
      }

      // Xóa cứng bị chặn — mời ẩn, đường đi giữ được cả outfit lẫn lịch sử mặc.
      const archiveOk = await confirm({
        title: 'Ẩn món đồ thay vì xóa?',
        message: `${err.message}\n\nMón sẽ biến khỏi tủ đồ và thống kê nhưng vẫn khôi phục lại được bất cứ lúc nào.`,
        confirmLabel: '🙈 Ẩn món đồ',
      });
      if (archiveOk) await handleArchive();
    }
  };

  const handleArchive = async () => {
    setBusy(true);
    setError(null);
    try {
      setItem(await itemsApi.archiveItem(item.id));
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const handleRestore = async () => {
    setBusy(true);
    setError(null);
    try {
      setItem(await itemsApi.restoreItem(item.id));
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const handleEditSave = async (payload) => {
    const updated = await itemsApi.updateItem(item.id, payload);
    setItem(updated);
    setEditing(false);
  };

  if (notFound) {
    return (
      <div>
        <EmptyState emoji="🔍">Không tìm thấy món đồ này. Có thể nó đã bị xóa.</EmptyState>
        <div style={{ marginTop: 16 }}>
          <Link to="/wardrobe" className="nb-btn">← Về tủ đồ</Link>
        </div>
      </div>
    );
  }

  if (!item) {
    return (
      <div>
        <ErrorBanner error={error} onDismiss={() => setError(null)} />
        <Loading>Đang mở món đồ...</Loading>
      </div>
    );
  }

  const wornToday = isWornToday(item.lastWornAt);
  const days = daysSince(item.lastWornAt);

  return (
    <div>
      <div className="page-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: 14, flexWrap: 'wrap' }}>
          <Link to="/wardrobe" className="nb-btn nb-btn--sm">← Tủ đồ</Link>
          <h1 className="page-title">
            {CATEGORY_EMOJIS[item.category]} {item.name}
          </h1>
        </div>
        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
          {item.archived ? (
            <Button variant="green" onClick={handleRestore} disabled={busy}>↩️ Khôi phục</Button>
          ) : (
            <>
              <Button variant="blue" onClick={() => setSharing(true)}>🔗 Chia sẻ</Button>
              <Button onClick={() => setEditing(true)}>✏️ Sửa</Button>
              <Button onClick={handleArchive} disabled={busy}>🙈 Ẩn</Button>
            </>
          )}
          <Button variant="danger" onClick={handleDelete} disabled={busy}>🗑️ Xóa</Button>
        </div>
      </div>

      <ErrorBanner error={error} onDismiss={() => setError(null)} />

      <div className="detail-masonry">
        <div className="nb-card">
          {item.imageUrl ? (
            <div className="item-photo" style={{ height: 280 }}>
              <img src={item.imageUrl} alt={item.name} style={{ objectFit: 'contain' }} />
            </div>
          ) : (
            <div
              className="item-photo"
              style={{
                height: 280,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 110,
                background: CATEGORY_COLORS[item.category] || 'var(--yellow)',
              }}
            >
              {CATEGORY_EMOJIS[item.category] || '👗'}
            </div>
          )}

          <div className="badge-row" style={{ marginTop: 14 }}>
            <Badge color="yellow">{label(CATEGORY_LABELS, item.category)}</Badge>
            <Badge color="blue">
              {SEASON_EMOJIS[item.season]} {label(SEASON_LABELS, item.season)}
            </Badge>
            <Badge color="purple">{label(STYLE_LABELS, item.style)}</Badge>
            {item.color && <Badge>🎨 {item.color}</Badge>}
            {item.colorTone && (
              <Badge color={TONE_BADGE_COLORS[item.colorTone]}>
                {TONE_EMOJIS[item.colorTone]} {label(TONE_LABELS, item.colorTone)}
              </Badge>
            )}
            {item.favorite && <Badge color="pink">⭐ Yêu thích</Badge>}
          </div>

          <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginTop: 16 }}>
            {item.blockReason === 'LAUNDRY' ? (
              <Button variant="green" onClick={handleWashed} disabled={busy}>
                ✅ Giặt xong
              </Button>
            ) : item.blockReason ? (
              // Hư hỏng / chưa dùng được: không có nút bấm một phát cho xong như giặt, người dùng
              // phải đổi trạng thái hoặc tình trạng ở ngay dưới.
              <Button disabled title={label(BLOCK_REASON_LABELS, item.blockReason)}>
                {BLOCK_REASON_EMOJIS[item.blockReason] ?? '⚠️'} Chưa mặc được (
                {label(BLOCK_REASON_LABELS, item.blockReason)})
              </Button>
            ) : wornToday ? (
              <Button disabled>✅ Đã mặc hôm nay</Button>
            ) : (
              <Button variant="green" onClick={handleWear} disabled={busy}>
                👣 Mặc hôm nay
              </Button>
            )}
            <Button variant={item.favorite ? 'pink' : undefined} onClick={handleToggleFavorite} disabled={busy}>
              {item.favorite ? '💔 Bỏ yêu thích' : '⭐ Yêu thích'}
            </Button>
          </div>
        </div>

        <div className="nb-card">
          <h3 className="chart-title">🚦 Trạng thái</h3>
            <p style={{ fontWeight: 600, color: 'var(--muted)', fontSize: 13.5, marginBottom: 10 }}>
              Hiện tại: <strong>{label(STATUS_LABELS, item.status)}</strong> ·{' '}
              <strong>{label(CONDITION_LABELS, item.condition)}</strong>
            </p>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 12 }}>
              {STATUS_OPTIONS.map((opt) => (
                <Button
                  key={opt.value}
                  size="sm"
                  variant={item.status === opt.value ? opt.variant ?? 'dark' : undefined}
                  disabled={busy || item.status === opt.value}
                  onClick={() => patchItem({ status: opt.value })}
                >
                  {opt.text}
                </Button>
              ))}
            </div>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              {CONDITION_OPTIONS.map((opt) => (
                <Button
                  key={opt.value}
                  size="sm"
                  variant={item.condition === opt.value ? opt.variant ?? 'dark' : undefined}
                  disabled={busy || item.condition === opt.value}
                  onClick={() => patchItem({ condition: opt.value })}
                >
                  {opt.text}
                </Button>
              ))}
            </div>
          </div>

          <div className="nb-card" style={{ marginBottom: 20 }}>
            <h3 className="chart-title">🪞 Thử đồ ảo</h3>
            {item.imageUrl ? (
              <>
                <p style={{ fontWeight: 600, color: 'var(--muted)', fontSize: 13.5, marginBottom: 12 }}>
                  Ghép món đồ này lên ảnh của bạn để xem thử.
                </p>
                <Button variant="pink" onClick={handleTryOn} disabled={tryingOn}>
                  {tryingOn ? '⏳ Đang ghép (1–2 phút)...' : '🪞 Thử lên người bạn'}
                </Button>
              </>
            ) : (
              <p style={{ fontWeight: 600, color: 'var(--muted)', fontSize: 13.5 }}>
                Thêm ảnh minh họa cho món đồ (nút ✏️ Sửa) để có thể thử lên người bạn.
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

          <div className="nb-card" style={{ marginBottom: 20 }}>
            <h3 className="chart-title">📈 Lịch sử mặc</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8, fontWeight: 600 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10 }}>
                <span>Tổng lượt mặc</span>
                <Badge color="orange">{item.wearCount ?? 0} lần</Badge>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10 }}>
                <span>Mặc lần cuối</span>
                <span style={{ color: 'var(--muted)' }}>
                  {formatDateTime(item.lastWornAt)}
                  {days !== null && days > 0 ? ` (${days} ngày trước)` : ''}
                </span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10 }}>
                <span>Thêm vào tủ</span>
                <span style={{ color: 'var(--muted)' }}>{formatDateTime(item.createdAt)}</span>
              </div>
            </div>
          </div>

          <div className="nb-card">
            <h3 className="chart-title">🧢 Thuộc các outfit</h3>
            {relatedOutfits.length === 0 ? (
              <p style={{ fontWeight: 600, color: 'var(--muted)' }}>
                Món này chưa thuộc outfit nào. Sang mục Outfit để phối nhé!
              </p>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {relatedOutfits.map((outfit) => (
                  <Link
                    key={outfit.id}
                    to="/outfits"
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      gap: 10,
                      fontWeight: 600,
                      textDecoration: 'none',
                      flexWrap: 'wrap',
                    }}
                  >
                    <span>🧢 {outfit.name}</span>
                    <Badge color="blue">
                      {SEASON_EMOJIS[outfit.season]} {label(SEASON_LABELS, outfit.season)}
                    </Badge>
                  </Link>
                ))}
              </div>
            )}
          </div>
      </div>

      {editing && <ItemFormModal item={item} onSave={handleEditSave} onClose={() => setEditing(false)} />}
      {sharing && (
        <ShareModal
          targetType="CLOTHING_ITEM"
          targetId={item.id}
          targetName={item.name}
          onClose={() => setSharing(false)}
          onToast={setToast}
        />
      )}
      <Toast message={toast} onDismiss={() => setToast(null)} />
    </div>
  );
}
