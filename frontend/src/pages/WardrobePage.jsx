import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { getAiStatus } from '../api/ai';
import * as itemsApi from '../api/clothingItems';
import * as tryOnApi from '../api/tryOn';
import ItemCard from '../components/ItemCard';
import BulkScanModal from '../components/BulkScanModal';
import ClosetSwitch from '../components/ClosetSwitch';
import ItemFormModal from '../components/ItemFormModal';
import { Button, EmptyState, ErrorBanner, Field, Loading, Pagination, Toast } from '../components/ui';
import { useConfirm } from '../context/ConfirmContext';
import {
  CATEGORY_COLORS,
  CATEGORY_EMOJIS,
  CATEGORY_LABELS,
  CONDITION_LABELS,
  SEASON_LABELS,
  STATUS_LABELS,
  STYLE_LABELS,
  TONE_LABELS,
  label,
} from '../utils/labels';
import { usePagedParams } from '../utils/usePagedParams';

const EMPTY_FILTERS = {
  keyword: '',
  category: '',
  season: '',
  style: '',
  condition: '',
  status: '',
  favorite: '',
  colorTone: '',
};

const PER_PAGE = 12; // 4 cột x 3 dòng

export default function WardrobePage() {
  const navigate = useNavigate();
  const confirm = useConfirm();
  const { searchParams, page, setPage, updateParams } = usePagedParams();

  // Bộ lọc đọc thẳng từ URL: mở lại link đã dán là thấy đúng kết quả đã lọc, không phải chọn lại.
  const filters = Object.fromEntries(
    Object.keys(EMPTY_FILTERS).map((key) => [key, searchParams.get(key) ?? ''])
  );

  const [itemsPage, setItemsPage] = useState(null);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [triedIds, setTriedIds] = useState(() => new Set());
  const [modal, setModal] = useState(null); // null | { item?: object }
  const [archived, setArchived] = useState([]);
  const [showArchived, setShowArchived] = useState(false);
  const [bulkScan, setBulkScan] = useState(false);
  const [aiReady, setAiReady] = useState(false);

  // Các món đã từng thử đồ (để hiện badge "🪞 Đã thử" trên card).
  const loadTriedIds = useCallback(() => {
    tryOnApi
      .listTryOns()
      .then((results) => setTriedIds(new Set(results.map((r) => r.clothingItemId).filter(Boolean))))
      .catch(() => setTriedIds(new Set()));
  }, []);

  // Chuỗi hóa bộ lọc để dùng làm dependency: `filters` là object mới sau mỗi lần render nên
  // so sánh tham chiếu sẽ khiến effect chạy vô hạn.
  const filterKey = JSON.stringify(filters);

  const load = useCallback(async () => {
    try {
      setItemsPage(await itemsApi.findItems({ ...JSON.parse(filterKey), page, size: PER_PAGE }));
    } catch (err) {
      setError(err.message);
    }
  }, [filterKey, page]);

  // Món đã ẩn nằm ở danh sách riêng, không trộn vào tủ đồ đang dùng.
  const loadArchived = useCallback(async () => {
    try {
      setArchived(await itemsApi.getArchivedItems());
    } catch {
      setArchived([]);
    }
  }, []);

  useEffect(() => {
    loadArchived();
  }, [loadArchived]);

  // Không có API key thì ẩn hẳn nút quét, tránh mời người dùng vào một tính năng sẽ báo lỗi.
  useEffect(() => {
    getAiStatus()
      .then((status) => setAiReady(Boolean(status.geminiConfigured)))
      .catch(() => setAiReady(false));
  }, []);

  // Gõ từ khóa thì chờ 300ms rồi mới gọi, tránh bắn một request cho mỗi phím.
  useEffect(() => {
    const timer = setTimeout(load, filters.keyword ? 300 : 0);
    return () => clearTimeout(timer);
  }, [load, filters.keyword]);

  useEffect(() => {
    loadTriedIds();
  }, [loadTriedIds]);

  // Đổi bộ lọc thì quay về trang 1: giữ nguyên trang 5 với kết quả mới gần như chắc chắn ra
  // danh sách rỗng.
  const setFilter = (key) => (e) => updateParams({ [key]: e.target.value || null, page: null });

  const toastOk = (message) => setNotice({ message, variant: 'success' });
  const toastErr = (message) => setNotice({ message, variant: 'error' });

  // PUT cần đủ payload; gửi lại các trường hiện có, chỉ đổi phần cần đổi.
  const patchItem = (item, changes) =>
    itemsApi.updateItem(item.id, {
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

  const handleSave = async (payload) => {
    const editing = Boolean(modal?.item);
    if (editing) {
      await itemsApi.updateItem(modal.item.id, payload);
    } else {
      await itemsApi.createItem(payload);
    }
    setModal(null);
    await load();
    toastOk(editing ? `Đã cập nhật "${payload.name}"! ✏️` : `Đã thêm "${payload.name}" vào tủ đồ! 🎉`);
  };

  /**
     * Xóa cứng trước; nếu server chặn vì món còn nằm trong outfit hoặc đã có lịch sử mặc
     * (mã CLOTHING_ITEM_IN_USE) thì mời người dùng ẩn thay vì xóa. Không hỏi trước "xóa hay ẩn"
     * vì phần lớn món mới thêm nhầm vẫn xóa được hẳn — hỏi sẽ thành thừa một bước.
     */
  const handleDelete = async (item) => {
    const ok = await confirm({
      title: 'Xóa món đồ?',
      message: `"${item.name}" sẽ bị xóa khỏi tủ đồ. Hành động này không thể hoàn tác.`,
      confirmLabel: '🗑️ Xóa luôn',
      danger: true,
    });
    if (!ok) return;

    try {
      await itemsApi.deleteItem(item.id);
      await load();
      toastOk(`Đã xóa "${item.name}" khỏi tủ đồ. 🗑️`);
      return;
    } catch (err) {
      if (err.code !== 'CLOTHING_ITEM_IN_USE') {
        toastErr(err.message);
        return;
      }

      const outfitCount = Number(err.details?.outfitCount ?? 0);
      const archiveOk = await confirm({
        title: 'Ẩn món đồ thay vì xóa?',
        message:
          `${err.message}\n\n` +
          (outfitCount > 0
            ? `${outfitCount} outfit chứa món này sẽ chuyển sang mục "Không khả dụng" cho tới khi bạn thay bằng món khác.`
            : 'Món sẽ biến khỏi tủ đồ và thống kê, nhưng lịch sử mặc vẫn được giữ nguyên.'),
        confirmLabel: '🙈 Ẩn món đồ',
      });
      if (!archiveOk) return;

      try {
        await itemsApi.archiveItem(item.id);
        await load();
        toastOk(`Đã ẩn "${item.name}". Xem lại ở mục Đã ẩn.`);
      } catch (archiveError) {
        toastErr(archiveError.message);
      }
    }
  };

  const handleRestore = async (item) => {
    try {
      await itemsApi.restoreItem(item.id);
      await loadArchived();
      await load();
      toastOk(`Đã khôi phục "${item.name}" về tủ đồ.`);
    } catch (err) {
      toastErr(err.message);
    }
  };

  const handleToggleFavorite = async (item) => {
    try {
      const updated = await itemsApi.setItemFavorite(item.id, !item.favorite);
      setItemsPage((current) =>
        current ? { ...current, content: current.content.map((i) => (i.id === updated.id ? updated : i)) } : current
      );
    } catch (err) {
      toastErr(err.message);
    }
  };

  const handleWear = async (item) => {
    try {
      const updated = await itemsApi.markItemWorn(item.id);
      setItemsPage((current) =>
        current ? { ...current, content: current.content.map((i) => (i.id === updated.id ? updated : i)) } : current
      );
      toastOk(`Đã ghi nhận mặc "${item.name}" hôm nay! 👣`);
    } catch (err) {
      toastErr(err.message);
    }
  };

  // Giặt xong: đưa món về trạng thái Sẵn sàng.
  const handleWashed = async (item) => {
    try {
      const updated = await patchItem(item, { status: 'AVAILABLE' });
      setItemsPage((current) =>
        current ? { ...current, content: current.content.map((i) => (i.id === updated.id ? updated : i)) } : current
      );
      toastOk(`"${item.name}" đã giặt xong, sẵn sàng mặc! ✅`);
    } catch (err) {
      toastErr(err.message);
    }
  };

  const renderFilterSelect = (key, labels, placeholder) => (
    <Field label={placeholder} className="filter-cell">
      <select className="nb-select" value={filters[key]} onChange={setFilter(key)} title={placeholder}>
        <option value="">Tất cả</option>
        {Object.entries(labels).map(([value, text]) => (
          <option key={value} value={value}>
            {text}
          </option>
        ))}
      </select>
    </Field>
  );

  return (
    <div>
      <div className="page-header">
        <ClosetSwitch />
        <div className="header-tools">
          {aiReady && (
            <Button variant="purple" onClick={() => setBulkScan(true)}>
              📸 Quét nhiều món
            </Button>
          )}
          <Button variant="primary" onClick={() => setModal({})}>
            ➕ Thêm món đồ
          </Button>
        </div>
      </div>

      <Toast
        message={notice?.message}
        variant={notice?.variant || 'success'}
        onDismiss={() => setNotice(null)}
      />
      <ErrorBanner error={error} onDismiss={() => setError(null)} />

      {/* Ô tìm kiếm chiếm trọn dòng trên; toàn bộ dropdown lọc + nút xóa lọc luôn nằm chung
          một dòng lưới bên dưới. */}
      <div className="filter-bar">
        <Field label="Tìm kiếm" className="filter-search">
          <input
            className="nb-input"
            value={filters.keyword}
            onChange={setFilter('keyword')}
            placeholder="🔍 Tìm theo tên hoặc màu..."
          />
        </Field>

        <div className="filter-grid filter-grid--7">
          {renderFilterSelect('category', CATEGORY_LABELS, 'Danh mục')}
          {renderFilterSelect('colorTone', TONE_LABELS, 'Tone màu')}
          {renderFilterSelect('season', SEASON_LABELS, 'Mùa')}
          {renderFilterSelect('style', STYLE_LABELS, 'Phong cách')}
          {renderFilterSelect('condition', CONDITION_LABELS, 'Tình trạng')}
          {renderFilterSelect('status', STATUS_LABELS, 'Trạng thái')}
          <Field label="Yêu thích" className="filter-cell">
            <select
              className="nb-select"
              value={filters.favorite}
              onChange={setFilter('favorite')}
              title="Yêu thích"
            >
              <option value="">Tất cả</option>
              <option value="true">⭐ Yêu thích</option>
              <option value="false">Chưa yêu thích</option>
            </select>
          </Field>
          <Button
            className="filter-reset"
            onClick={() =>
              updateParams({
                ...Object.fromEntries(Object.keys(EMPTY_FILTERS).map((key) => [key, null])),
                page: null,
              })
            }
          >
            🔄 Xóa lọc
          </Button>
        </div>
      </div>

      {itemsPage === null ? (
        <Loading />
      ) : itemsPage.content.length === 0 ? (
        <EmptyState emoji="🧺">
          Chưa có món đồ nào. Bấm "Thêm món đồ" để bắt đầu xây dựng tủ đồ của bạn!
        </EmptyState>
      ) : (
        (() => {
          const pageCount = itemsPage.totalPages || 1;
          return (
            <>
              <div className="card-grid">
                {itemsPage.content.map((item) => (
                  <ItemCard
                    key={item.id}
                    item={item}
                    tried={triedIds.has(item.id)}
                    onOpen={(i) => navigate(`/wardrobe/${i.id}`)}
                    onEdit={(i) => setModal({ item: i })}
                    onDelete={handleDelete}
                    onToggleFavorite={handleToggleFavorite}
                    onWear={handleWear}
                    onWashed={handleWashed}
                  />
                ))}
              </div>
              <Pagination page={page} pageCount={pageCount} onChange={setPage} />
            </>
          );
        })()
      )}

      {archived.length > 0 && (
        <div className="archived-section">
          <button
            type="button"
            className="archived-toggle"
            onClick={() => setShowArchived((v) => !v)}
            aria-expanded={showArchived}
          >
            {showArchived ? '▾' : '▸'} 🙈 Đã ẩn ({archived.length})
            <span className="archived-toggle-hint">
              Món không xóa được vì còn nằm trong outfit hoặc đã có lịch sử mặc.
            </span>
          </button>

          {showArchived && (
            <div className="archived-list">
              {archived.map((item) => (
                <div key={item.id} className="archived-row">
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
                  <div className="archived-row-main">
                    <Link className="archived-row-name" to={`/wardrobe/${item.id}`}>
                      {item.name}
                    </Link>
                    <div className="archived-row-meta">
                      {label(CATEGORY_LABELS, item.category)} · 👣 {item.wearCount ?? 0} lượt mặc
                    </div>
                  </div>
                  <Button size="sm" variant="green" onClick={() => handleRestore(item)}>
                    ↩️ Khôi phục
                  </Button>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {modal && <ItemFormModal item={modal.item} onSave={handleSave} onClose={() => setModal(null)} />}
      {bulkScan && (
        <BulkScanModal
          onClose={() => setBulkScan(false)}
          onDone={async (count) => {
            setBulkScan(false);
            await load();
            toastOk(`Đã thêm ${count} món vào tủ đồ! 🎉`);
          }}
        />
      )}
    </div>
  );
}
