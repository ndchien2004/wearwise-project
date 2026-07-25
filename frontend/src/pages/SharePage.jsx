import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import * as sharesApi from '../api/shares';
import OutfitVisual from '../components/OutfitVisual';
import { Badge, Button, EmptyState, ErrorBanner, Field, Loading, Toast } from '../components/ui';
import { useAuth } from '../context/AuthContext';
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
import { formatDateTime } from '../utils/date';

/** Lấy phần mã từ chuỗi người dùng dán vào (có thể là cả link). */
function extractCode(input) {
  const trimmed = input.trim().replace(/\/+$/, '');
  const lastSegment = trimmed.slice(trimmed.lastIndexOf('/') + 1);
  return lastSegment.replace(/[\s-]/g, '').toUpperCase();
}

/** Xem trước nội dung một mã và chép về tủ đồ. */
function SharePreview({ code }) {
  const navigate = useNavigate();
  const { username } = useAuth();

  const [preview, setPreview] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState(null);

  useEffect(() => {
    setPreview(null);
    setResult(null);
    setError(null);
    sharesApi
      .getSharePreview(code)
      .then(setPreview)
      .catch((err) => setError(err.message));
  }, [code]);

  const handleImport = async () => {
    setBusy(true);
    setError(null);
    try {
      setResult(await sharesApi.importShare(code));
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  if (error && !preview) {
    return (
      <div>
        <ErrorBanner error={error} onDismiss={() => setError(null)} />
        <EmptyState emoji="🔗">
          Mã chia sẻ này không dùng được. Hãy xin người gửi một mã mới.
        </EmptyState>
        <div style={{ marginTop: 16 }}>
          <Link to="/share" className="nb-btn">← Về trang chia sẻ</Link>
        </div>
      </div>
    );
  }

  if (!preview) return <Loading>Đang mở mã chia sẻ...</Loading>;

  const isOutfit = preview.targetType === 'OUTFIT';
  const outfit = preview.outfit;
  const item = preview.item;
  const items = isOutfit ? outfit.clothingItems : [item];

  return (
    <div>
      <ErrorBanner error={error} onDismiss={() => setError(null)} />

      <div className="two-col">
        <div className="nb-card">
          {isOutfit ? (
            <OutfitVisual outfit={outfit} />
          ) : item.imageUrl ? (
            <div className="item-photo" style={{ height: 280 }}>
              <img src={item.imageUrl} alt={item.name} style={{ objectFit: 'contain' }} />
            </div>
          ) : (
            <div
              className="item-photo item-photo--placeholder"
              style={{ background: CATEGORY_COLORS[item.category] || 'var(--yellow)' }}
            >
              {CATEGORY_EMOJIS[item.category] || '👗'}
            </div>
          )}

          <h2 style={{ fontSize: 22, marginTop: 14 }}>
            {isOutfit ? '🧢' : CATEGORY_EMOJIS[item.category]} {isOutfit ? outfit.name : item.name}
          </h2>

          <div className="badge-row" style={{ marginTop: 10 }}>
            <Badge color="blue">
              {SEASON_EMOJIS[isOutfit ? outfit.season : item.season]}{' '}
              {label(SEASON_LABELS, isOutfit ? outfit.season : item.season)}
            </Badge>
            <Badge color="purple">{label(STYLE_LABELS, isOutfit ? outfit.style : item.style)}</Badge>
            {!isOutfit && <Badge color="yellow">{label(CATEGORY_LABELS, item.category)}</Badge>}
          </div>

          {isOutfit && outfit.description && (
            <p style={{ fontWeight: 600, color: 'var(--muted)', fontSize: 13.5, marginTop: 12 }}>
              {outfit.description}
            </p>
          )}
        </div>

        <div>
          <div className="nb-card" style={{ marginBottom: 20 }}>
            <h3 className="chart-title">📤 Người chia sẻ</h3>
            <div className="badge-row" style={{ marginBottom: 12 }}>
              <Badge color="pink">👤 {preview.ownerUsername}</Badge>
              <Badge color="purple">📥 {preview.importCount} lượt thêm</Badge>
              {preview.expiresAt && <Badge color="orange">⏳ Hết hạn {formatDateTime(preview.expiresAt)}</Badge>}
            </div>

            {preview.mine ? (
              <div className="notice-banner" style={{ background: 'var(--blue)' }}>
                ℹ️ Đây là mã do chính bạn tạo nên không cần chép lại. Gửi mã{' '}
                <strong>{preview.code}</strong> cho người bạn muốn chia sẻ.
              </div>
            ) : result ? (
              <>
                <div className="notice-banner" style={{ background: 'var(--green)' }}>
                  ✅ {result.message}
                  {result.itemsReused > 0 && (
                    <> ({result.itemsCreated} món mới, {result.itemsReused} món đã có sẵn)</>
                  )}
                </div>
                <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', marginTop: 12 }}>
                  <Button
                    variant="primary"
                    onClick={() =>
                      navigate(
                        result.targetType === 'OUTFIT'
                          ? `/outfits/${result.outfit.id}`
                          : `/wardrobe/${result.item.id}`
                      )
                    }
                  >
                    👀 Xem trong tủ đồ
                  </Button>
                  <Link to="/share" className="nb-btn">🔗 Nhập mã khác</Link>
                </div>
              </>
            ) : (
              <>
                <p style={{ fontWeight: 600, color: 'var(--muted)', fontSize: 13.5, marginBottom: 14 }}>
                  Bấm thêm để chép {isOutfit ? 'cả bộ này' : 'món này'} vào tủ đồ của{' '}
                  <strong>{username}</strong>. Bản sao là của riêng bạn — sửa hay xóa đều không ảnh
                  hưởng tới người chia sẻ, và lượt mặc bắt đầu lại từ 0.
                </p>
                <Button variant="primary" onClick={handleImport} disabled={busy}>
                  {busy ? '⏳ Đang thêm...' : '➕ Thêm vào tủ đồ của tôi'}
                </Button>
              </>
            )}
          </div>

          <div className="nb-card">
            <h3 className="chart-title">👕 Sẽ được thêm ({items.length})</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {items.map((entry) => (
                <div key={entry.id} className="outfit-item-row">
                  {entry.imageUrl ? (
                    <img className="outfit-item-thumb" src={entry.imageUrl} alt={entry.name} />
                  ) : (
                    <span
                      className="outfit-item-thumb outfit-item-thumb--emoji"
                      style={{ background: CATEGORY_COLORS[entry.category] || 'var(--yellow)' }}
                    >
                      {CATEGORY_EMOJIS[entry.category] || '👗'}
                    </span>
                  )}
                  <span className="outfit-item-row-name">{entry.name}</span>
                  <Badge color="yellow">{label(CATEGORY_LABELS, entry.category)}</Badge>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

/** Trang gốc: nhập mã người khác gửi + quản lý mã của chính mình. */
function ShareHome() {
  const navigate = useNavigate();
  const confirm = useConfirm();

  const [code, setCode] = useState('');
  const [shares, setShares] = useState([]);
  const [error, setError] = useState(null);
  const [loaded, setLoaded] = useState(false);
  const [toast, setToast] = useState(null);
  const [copied, setCopied] = useState(null);

  const load = useCallback(() => {
    sharesApi
      .listMyShares()
      .then(setShares)
      .catch((err) => setError(err.message))
      .finally(() => setLoaded(true));
  }, []);

  useEffect(load, [load]);

  const handleOpen = (e) => {
    e.preventDefault();
    const parsed = extractCode(code);
    if (!parsed) {
      setError('Hãy nhập mã chia sẻ hoặc dán link được gửi cho bạn.');
      return;
    }
    navigate(`/share/${parsed}`);
  };

  const handleCopy = async (share) => {
    try {
      await navigator.clipboard.writeText(share.code);
      setCopied(share.code);
      setTimeout(() => setCopied(null), 2000);
    } catch {
      setError('Trình duyệt không cho phép sao chép tự động. Hãy bôi đen mã và copy thủ công.');
    }
  };

  const handleRevoke = async (share) => {
    const ok = await confirm({
      title: 'Thu hồi mã chia sẻ?',
      message: `Mã "${share.code}" cho ${share.targetName} sẽ không dùng được nữa.`,
      confirmLabel: '🚫 Thu hồi',
      danger: true,
    });
    if (!ok) return;

    try {
      await sharesApi.revokeShare(share.code);
      setToast('Đã thu hồi mã chia sẻ.');
      load();
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <div>
      <ErrorBanner error={error} onDismiss={() => setError(null)} />

      <div className="nb-card" style={{ marginBottom: 20 }}>
        <h3 className="chart-title">📥 Nhận trang phục được chia sẻ</h3>
        <form onSubmit={handleOpen} className="share-input-row">
          <Field label="Mã chia sẻ" className="share-input-field">
            <input
              className="nb-input"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              placeholder="vd: K7QM2XPA"
              maxLength={200}
            />
          </Field>
          <button type="submit" className="nb-btn nb-btn--primary">🔍 Xem trước</button>
        </form>
        <p className="auth-hint" style={{ marginTop: 0 }}>
          Mã gồm 8 ký tự, không phân biệt hoa thường. Bạn sẽ xem được trang phục trước, muốn thì
          mới thêm vào tủ đồ.
        </p>
      </div>

      <div className="nb-card">
        <h3 className="chart-title">📤 Mã tôi đã tạo ({shares.length})</h3>

        {!loaded ? (
          <Loading>Đang tải...</Loading>
        ) : shares.length === 0 ? (
          <EmptyState emoji="🔗">
            Chưa có mã nào. Mở một outfit hoặc món đồ rồi bấm "🔗 Chia sẻ" để tạo mã gửi bạn bè.
          </EmptyState>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {shares.map((share) => (
              <div key={share.code} className={`share-row ${share.active ? '' : 'is-inactive'}`}>
                {share.targetImageUrl ? (
                  <img className="outfit-item-thumb" src={share.targetImageUrl} alt={share.targetName} />
                ) : (
                  <span className="outfit-item-thumb outfit-item-thumb--emoji" style={{ background: 'var(--pink)' }}>
                    {share.targetType === 'OUTFIT' ? '🧢' : '👕'}
                  </span>
                )}

                <div className="share-row-main">
                  <Link
                    className="share-row-name"
                    to={
                      share.targetType === 'OUTFIT'
                        ? `/outfits/${share.targetId}`
                        : `/wardrobe/${share.targetId}`
                    }
                  >
                    {share.targetName}
                  </Link>
                  <div className="share-row-meta">
                    <code className="share-row-code">{share.code}</code>
                    <span>📥 {share.importCount}</span>
                    <span>{share.targetType === 'OUTFIT' ? `${share.itemCount} món` : 'Món lẻ'}</span>
                    {!share.active && <Badge color="muted">Đã thu hồi / hết hạn</Badge>}
                  </div>
                </div>

                {share.active && (
                  <div className="share-row-actions">
                    <Button size="sm" onClick={() => handleCopy(share)}>
                      {copied === share.code ? '✅ Đã chép' : '📋 Chép mã'}
                    </Button>
                    <Button size="sm" variant="danger" onClick={() => handleRevoke(share)}>
                      🚫
                    </Button>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      <Toast message={toast} onDismiss={() => setToast(null)} />
    </div>
  );
}

export default function SharePage() {
  const { code } = useParams();

  return (
    <div>
      <div className="page-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: 14, flexWrap: 'wrap' }}>
          {code && <Link to="/share" className="nb-btn nb-btn--sm">← Chia sẻ</Link>}
          <h1 className="page-title">🔗 Chia sẻ trang phục</h1>
        </div>
      </div>

      {code ? <SharePreview code={code} /> : <ShareHome />}
    </div>
  );
}
