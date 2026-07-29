import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import * as itemsApi from '../api/clothingItems';
import * as outfitsApi from '../api/outfits';
import * as tryOnApi from '../api/tryOn';
import ImageLightbox from '../components/ImageLightbox';
import { Badge, Button, EmptyState, ErrorBanner, Loading } from '../components/ui';
import { useConfirm } from '../context/ConfirmContext';
import { formatDateTime } from '../utils/date';
import { CATEGORY_EMOJIS, CATEGORY_LABELS } from '../utils/labels';
import { usePagedParams } from '../utils/usePagedParams';

const GUIDELINES = [
  'Ảnh rõ nét, đủ sáng, không bị mờ hay nhòe.',
  'Chụp thẳng, thấy rõ toàn thân hoặc ít nhất nửa thân trên.',
  'Chỉ có một người trong ảnh, tư thế đứng tự nhiên.',
  'Nền đơn giản, không bị che khuất cơ thể.',
  'Định dạng JPG hoặc PNG, tối đa 10MB, mỗi cạnh ≥ 300px.',
];

const ITEMS_PER_PAGE = 4; // 2 cột x 2 dòng — vừa cột phải, không đẩy trang dài ra

export default function TryOnPage() {
  const confirm = useConfirm();
  const fileInputRef = useRef(null);

  const [profile, setProfile] = useState(null);
  const [results, setResults] = useState([]);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [generatingId, setGeneratingId] = useState(null);

  /** Ảnh đang hiển thị ở khung so sánh. NULL nghĩa là chưa ghép lần nào trong phiên này. */
  const [activeResult, setActiveResult] = useState(null);
  /** Ghép món tiếp theo chồng lên ảnh đang xem, thay vì bắt đầu lại từ ảnh cơ thể gốc. */
  const [layerMode, setLayerMode] = useState(true);
  /** Ảnh đang mở ở chế độ phóng to: { src, alt }. */
  const [zoomed, setZoomed] = useState(null);

  const { searchParams, page, setPage, updateParams } = usePagedParams();
  const mode = searchParams.get('mode') === 'outfit' ? 'outfit' : 'item';
  const keyword = searchParams.get('q') ?? '';
  const category = searchParams.get('cat') ?? '';

  const [itemsPage, setItemsPage] = useState(null);
  const [outfits, setOutfits] = useState(null);

  useEffect(() => {
    tryOnApi.getTryOnProfile().then(setProfile).catch((err) => setError(err.message));
    tryOnApi.listTryOns().then(setResults).catch(() => setResults([]));
  }, []);

  // hasImage=true lọc ngay ở database. Lọc phía client sau khi server đã cắt trang thì mỗi trang
  // sẽ thiếu món một cách ngẫu nhiên — trang này 4 món, trang kia 6.
  useEffect(() => {
    if (mode !== 'item') return;
    // Gõ từ khóa thì chờ một nhịp rồi mới gọi, tránh bắn một request cho mỗi phím.
    const timer = setTimeout(
      () => {
        itemsApi
          .findItems({ hasImage: true, keyword, category, page, size: ITEMS_PER_PAGE })
          .then(setItemsPage)
          .catch(() => setItemsPage({ content: [], totalPages: 0, totalElements: 0 }));
      },
      keyword ? 300 : 0
    );
    return () => clearTimeout(timer);
  }, [mode, page, keyword, category]);

  // Outfit không phân trang: một người thường chỉ có vài chục bộ, và khung chọn cần thấy hết
  // để so sánh. Nếu số lượng lớn lên thì chuyển sang phân trang server như bên món lẻ.
  useEffect(() => {
    if (mode !== 'outfit' || outfits !== null) return;
    outfitsApi.findOutfits().then(setOutfits).catch(() => setOutfits([]));
  }, [mode, outfits]);

  const switchMode = (nextMode) => updateParams({ mode: nextMode === 'item' ? null : nextMode, page: null });

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
      // Ảnh cơ thể mới thì mọi kết quả đang xem đều thuộc về ảnh cũ — dọn khung so sánh.
      setActiveResult(null);
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
      setActiveResult(null);
    } catch (err) {
      setError(err.message);
    }
  };

  /**
   * Ghép và đưa kết quả lên khung so sánh ngay lập tức. Trước đây ảnh chỉ rơi xuống bộ sưu tập
   * phía dưới, người dùng phải cuộn xuống mới thấy — đúng lúc đang muốn xem ngay kết quả.
   */
  const runTryOn = useCallback(
    async (kind, target) => {
      setError(null);
      setNotice(null);
      setGeneratingId(`${kind}-${target.id}`);

      // Chỉ ghép chồng khi đang thật sự xem một kết quả và người dùng bật chế độ đó.
      const baseResultId = layerMode && activeResult ? activeResult.id : undefined;

      try {
        const result =
          kind === 'outfit'
            ? await tryOnApi.generateOutfitTryOn(target.id, baseResultId)
            : await tryOnApi.generateTryOn(target.id, baseResultId);

        setActiveResult(result);
        setResults((list) => [result, ...list]);
        setNotice(
          baseResultId
            ? `Đã mặc thêm "${target.name}" lên ảnh vừa ghép! 🎉`
            : `Đã ghép "${target.name}" lên ảnh của bạn! 🎉`
        );
      } catch (err) {
        setError(err.message);
      } finally {
        setGeneratingId(null);
      }
    },
    [layerMode, activeResult]
  );

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
      if (activeResult?.id === result.id) setActiveResult(null);
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

  const busy = generatingId !== null;
  const beforeImage = activeResult ? activeResult.baseImageUrl : profile.bodyPhotoUrl;

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">🪞 Thử đồ ảo</h1>

        {profile.hasBodyPhoto && (
          <div className="page-switch" role="tablist" aria-label="Kiểu thử đồ">
            <button
              type="button"
              role="tab"
              aria-selected={mode === 'item'}
              className={`page-switch-btn ${mode === 'item' ? 'is-active' : ''}`}
              onClick={() => switchMode('item')}
            >
              <span className="page-switch-emoji">👕</span>
              Từng món
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={mode === 'outfit'}
              className={`page-switch-btn ${mode === 'outfit' ? 'is-active' : ''}`}
              onClick={() => switchMode('outfit')}
            >
              <span className="page-switch-emoji">🧢</span>
              Cả bộ
            </button>
          </div>
        )}
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

      {!profile.hasBodyPhoto ? (
        <UploadPrompt uploading={uploading} onPick={handlePickFile} />
      ) : (
        <>
          {/* Hai cột: khung so sánh bên trái, ô chọn đồ bên phải — cả hai cùng nằm trong một
              màn hình, không phải cuộn xuống mới thấy đồ để chọn. */}
          <div className="tryon-layout">
            {/* ---------- Khung so sánh trước / sau ---------- */}
            <div className="nb-card tryon-stage">
              <div className="tryon-stage-head">
                <h3 className="chart-title" style={{ marginBottom: 0 }}>
                  {activeResult ? '🔍 So sánh trước và sau' : '📸 Ảnh của bạn'}
                </h3>
                <div className="tryon-stage-actions">
                  <Button size="sm" onClick={handlePickFile} disabled={uploading || busy}>
                    {uploading ? '⏳ Đang tải...' : '🔄 Đổi ảnh'}
                  </Button>
                  <Button size="sm" variant="danger" onClick={handleRemovePhoto} disabled={uploading || busy}>
                    🗑️ Xóa ảnh
                  </Button>
                </div>
              </div>

              <div className="tryon-compare">
                <figure className="tryon-pane">
                  <figcaption className="tryon-pane-label">TRƯỚC</figcaption>
                  <button
                    type="button"
                    className="tryon-pane-img"
                    onClick={() => setZoomed({ src: beforeImage, alt: 'Ảnh trước khi ghép' })}
                    title="Bấm để phóng to"
                  >
                    <img src={beforeImage} alt="Ảnh trước khi ghép" />
                    <span className="tryon-zoom-badge" aria-hidden="true">
                      🔍
                    </span>
                  </button>
                </figure>

                <figure className="tryon-pane">
                  <figcaption className="tryon-pane-label tryon-pane-label--after">SAU</figcaption>
                  {busy ? (
                    <div className="tryon-pane-img tryon-pane-img--placeholder">
                      <span className="tryon-spinner" aria-hidden="true">
                        ⏳
                      </span>
                      <strong>Đang ghép trang phục...</strong>
                      <span className="muted-note">Có thể mất tới 1–2 phút</span>
                    </div>
                  ) : activeResult ? (
                    <button
                      type="button"
                      className="tryon-pane-img"
                      onClick={() => setZoomed({ src: activeResult.resultImageUrl, alt: 'Ảnh sau khi ghép' })}
                      title="Bấm để phóng to"
                    >
                      <img src={activeResult.resultImageUrl} alt="Ảnh sau khi ghép" />
                      <span className="tryon-zoom-badge" aria-hidden="true">
                        🔍
                      </span>
                    </button>
                  ) : (
                    <div className="tryon-pane-img tryon-pane-img--placeholder">
                      <span className="tryon-spinner" aria-hidden="true">
                        ✨
                      </span>
                      <strong>Chưa ghép món nào</strong>
                      <span className="muted-note">Chọn một món đồ bên dưới để bắt đầu</span>
                    </div>
                  )}
                </figure>
              </div>

              {activeResult && (
                <div className="tryon-layer-bar">
                  <label className="nb-checkbox-row">
                    <input
                      type="checkbox"
                      checked={layerMode}
                      onChange={(e) => setLayerMode(e.target.checked)}
                      disabled={busy}
                    />
                    Mặc chồng lên ảnh này
                  </label>
                  <span className="muted-note">
                    {layerMode
                      ? 'Món chọn tiếp theo sẽ mặc thêm lên ảnh bên phải — vd đã có quần, mặc thêm áo.'
                      : 'Món chọn tiếp theo sẽ ghép lại từ đầu, trên ảnh cơ thể gốc.'}
                  </span>
                  <Button size="sm" onClick={() => setActiveResult(null)} disabled={busy}>
                    ↺ Về ảnh gốc
                  </Button>
                </div>
              )}
            </div>

            {/* ---------- Chọn đồ ---------- */}
            <div className="nb-card tryon-picker">
              <div className="tryon-picker-head">
                <h3 className="chart-title" style={{ marginBottom: 0 }}>
                  {mode === 'outfit' ? '🧢 Chọn bộ' : '👗 Chọn món đồ'}
                </h3>

                <div className="tryon-filter">
                  <input
                    className="nb-input"
                    value={keyword}
                    onChange={(e) => updateParams({ q: e.target.value || null, page: null })}
                    placeholder="Tìm theo tên..."
                    aria-label="Tìm nhanh"
                  />
                  {mode === 'item' && (
                    <select
                      className="nb-select"
                      value={category}
                      onChange={(e) => updateParams({ cat: e.target.value || null, page: null })}
                      aria-label="Lọc theo danh mục"
                    >
                      <option value="">Mọi loại</option>
                      {Object.entries(CATEGORY_LABELS).map(([key, label]) => (
                        <option key={key} value={key}>
                          {label}
                        </option>
                      ))}
                    </select>
                  )}
                </div>
              </div>

              {mode === 'outfit' ? (
                <OutfitPicker
                  outfits={outfits}
                  keyword={keyword}
                  busy={busy}
                  generatingId={generatingId}
                  activeOutfitId={activeResult?.outfitId}
                  onPick={(outfit) => runTryOn('outfit', outfit)}
                />
              ) : (
                <ItemPicker
                  itemsPage={itemsPage}
                  page={page}
                  onPageChange={setPage}
                  busy={busy}
                  generatingId={generatingId}
                  activeItemId={activeResult?.clothingItemId}
                  onPick={(item) => runTryOn('item', item)}
                />
              )}
            </div>
          </div>

          <details className="nb-card tryon-guidelines-card">
            <summary>✅ Ảnh của bạn nên như thế nào?</summary>
            <ul>
              {GUIDELINES.map((g) => (
                <li key={g}>{g}</li>
              ))}
            </ul>
          </details>
        </>
      )}

      {/* ---------- Bộ sưu tập ---------- */}
      <h2 className="section-heading" style={{ marginTop: 28 }}>
        🖼️ Bộ sưu tập thử đồ
      </h2>
      {results.length === 0 ? (
        <EmptyState emoji="✨">Chưa có ảnh thử đồ nào. Chọn một món đồ ở trên để bắt đầu!</EmptyState>
      ) : (
        <div className="card-grid">
          {results.map((result) => (
            <div key={result.id} className={`nb-card item-card ${activeResult?.id === result.id ? 'is-active' : ''}`}>
              <button
                type="button"
                className="tryon-result-photo"
                onClick={() => setZoomed({ src: result.resultImageUrl, alt: result.clothingItemName || 'Ảnh thử đồ' })}
                title="Bấm để phóng to"
              >
                <img src={result.resultImageUrl} alt={result.clothingItemName || 'Ảnh thử đồ'} />
              </button>
              <div className="item-name" title={result.outfitName || result.clothingItemName || ''}>
                {result.outfitName ? `🧢 ${result.outfitName}` : result.clothingItemName || 'Món đồ đã xóa'}
              </div>
              <div className="badge-row">
                <Badge color="purple">🕐 {formatDateTime(result.createdAt)}</Badge>
                {result.baseResultId && <Badge color="cyan">🧅 Mặc chồng</Badge>}
              </div>
              <div className="card-actions">
                <Button size="sm" variant="primary" onClick={() => setActiveResult(result)}>
                  ↑ Đưa lên khung
                </Button>
                <Button size="sm" variant="danger" onClick={() => handleDeleteResult(result)}>
                  🗑️
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}

      {zoomed && <ImageLightbox src={zoomed.src} alt={zoomed.alt} onClose={() => setZoomed(null)} />}
    </div>
  );
}

/* ---------------- Các khối con ---------------- */

function UploadPrompt({ uploading, onPick }) {
  return (
    <div className="nb-card" style={{ marginBottom: 28 }}>
      <h3 className="chart-title">📸 Ảnh của bạn</h3>
      <button type="button" className="tryon-dropzone" onClick={onPick} disabled={uploading}>
        <span className="tryon-dropzone-emoji">{uploading ? '⏳' : '⬆️'}</span>
        <strong>{uploading ? 'Đang tải ảnh lên...' : 'Bấm để tải ảnh của bạn lên'}</strong>
        <span style={{ color: 'var(--muted)', fontWeight: 600, fontSize: 13.5 }}>
          Chỉ cần làm một lần. JPG/PNG, tối đa 10MB.
        </span>
      </button>

      <div className="tryon-guidelines">
        <strong>✅ Ảnh đạt yêu cầu khi:</strong>
        <ul>
          {GUIDELINES.map((g) => (
            <li key={g}>{g}</li>
          ))}
        </ul>
      </div>
    </div>
  );
}

/**
 * Khung cuộn trang bằng hai mũi tên hai bên, thay cho dãy số trang phía dưới.
 *
 * Ở đây người dùng đang lướt xem để chọn chứ không nhảy tới một trang cụ thể, nên hai nút
 * trái/phải sát mép là thao tác đúng — và chúng không chiếm thêm chiều cao, thứ đang phải
 * chia cho khung so sánh bên cạnh.
 */
function PagerFrame({ page, pageCount, onPageChange, children }) {
  return (
    <>
      <div className="tryon-picker-body">
        <button
          type="button"
          className="tryon-arrow"
          onClick={() => onPageChange(page - 1)}
          disabled={page <= 0}
          aria-label="Trang trước"
        >
          ‹
        </button>

        {children}

        <button
          type="button"
          className="tryon-arrow"
          onClick={() => onPageChange(page + 1)}
          disabled={page >= pageCount - 1}
          aria-label="Trang sau"
        >
          ›
        </button>
      </div>

      {pageCount > 1 && (
        <p className="tryon-pager-label">
          Trang {page + 1} / {pageCount}
        </p>
      )}
    </>
  );
}

function ItemPicker({ itemsPage, page, onPageChange, busy, generatingId, activeItemId, onPick }) {
  if (itemsPage === null) return <Loading>Đang tải tủ đồ...</Loading>;

  if (itemsPage.totalElements === 0) {
    return (
      <EmptyState emoji="🖼️">
        Không có món nào khớp. Thử bỏ bớt bộ lọc, hoặc{' '}
        <Link to="/wardrobe" style={{ fontWeight: 700 }}>
          thêm ảnh cho đồ trong tủ
        </Link>
        .
      </EmptyState>
    );
  }

  return (
    <PagerFrame page={page} pageCount={itemsPage.totalPages || 1} onPageChange={onPageChange}>
      <div className="tryon-item-grid">
        {itemsPage.content.map((item) => {
          const generating = generatingId === `item-${item.id}`;
          const active = activeItemId === item.id;
          return (
            <div
              key={item.id}
              className={`tryon-item ${generating ? 'is-generating' : ''} ${active ? 'is-wearing' : ''}`}
            >
              <div className="tryon-item-photo">
                <img src={item.imageUrl} alt={item.name} />
                {/* Nhãn nổi trên ảnh: nhìn một cái là biết món nào đang trên người */}
                {generating && <span className="tryon-item-flag">⏳ Đang ghép</span>}
                {!generating && active && <span className="tryon-item-flag is-wearing">✓ Đang mặc</span>}
              </div>
              <div className="tryon-item-name" title={item.name}>
                {CATEGORY_EMOJIS[item.category]} {item.name}
              </div>
              <Button size="sm" variant={active ? 'dark' : 'green'} onClick={() => onPick(item)} disabled={busy}>
                {generating ? '⏳ Đang ghép...' : active ? '↻ Ghép lại' : '✨ Thử'}
              </Button>
            </div>
          );
        })}
      </div>
    </PagerFrame>
  );
}

function OutfitPicker({ outfits, keyword, busy, generatingId, activeOutfitId, onPick }) {
  const [page, setPage] = useState(0);

  if (outfits === null) return <Loading>Đang tải outfit...</Loading>;

  // Bộ không có món nào kèm ảnh thì server cũng sẽ từ chối — lọc sẵn để khỏi mời người dùng
  // bấm vào một nút chắc chắn báo lỗi.
  const needle = keyword.trim().toLowerCase();
  const wearable = outfits
    .filter((outfit) => (outfit.clothingItems || []).some((item) => item.imageUrl))
    .filter((outfit) => !needle || outfit.name.toLowerCase().includes(needle));

  if (wearable.length === 0) {
    return (
      <EmptyState emoji="🧢">
        Không có bộ nào khớp.{' '}
        <Link to="/outfits" style={{ fontWeight: 700 }}>
          Phối một bộ
        </Link>{' '}
        từ những món đã có ảnh rồi quay lại nhé.
      </EmptyState>
    );
  }

  // Outfit tải về một lần rồi cắt trang tại chỗ: danh sách nhỏ (vài chục bộ) và endpoint
  // /api/outfits chưa phân trang. Chuyển sang phân trang server nếu số lượng lớn lên.
  const pageCount = Math.max(1, Math.ceil(wearable.length / ITEMS_PER_PAGE));
  const safePage = Math.min(page, pageCount - 1);
  const shown = wearable.slice(safePage * ITEMS_PER_PAGE, (safePage + 1) * ITEMS_PER_PAGE);

  return (
    <PagerFrame page={safePage} pageCount={pageCount} onPageChange={setPage}>
      <div className="tryon-item-grid">
        {shown.map((outfit) => {
          const photos = (outfit.clothingItems || []).filter((item) => item.imageUrl);
          const generating = generatingId === `outfit-${outfit.id}`;
          const active = activeOutfitId === outfit.id;
          return (
            <div
              key={outfit.id}
              className={`tryon-item ${generating ? 'is-generating' : ''} ${active ? 'is-wearing' : ''}`}
            >
              <div className="tryon-outfit-thumbs">
                {photos.slice(0, 4).map((item) => (
                  <img key={item.id} src={item.imageUrl} alt={item.name} />
                ))}
                {generating && <span className="tryon-item-flag">⏳ Đang ghép</span>}
                {!generating && active && <span className="tryon-item-flag is-wearing">✓ Đang mặc</span>}
              </div>
              <div className="tryon-item-name" title={outfit.name}>
                🧢 {outfit.name} · {photos.length} món
              </div>
              <Button size="sm" variant={active ? 'dark' : 'green'} onClick={() => onPick(outfit)} disabled={busy}>
                {generating ? '⏳ Đang ghép...' : active ? '↻ Ghép lại' : '✨ Thử cả bộ'}
              </Button>
            </div>
          );
        })}
      </div>
    </PagerFrame>
  );
}
