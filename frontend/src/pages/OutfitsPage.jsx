import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import * as outfitsApi from '../api/outfits';
import * as tryOnApi from '../api/tryOn';
import OutfitCard from '../components/OutfitCard';
import OutfitFormModal from '../components/OutfitFormModal';
import { Button, EmptyState, ErrorBanner, Field, Loading } from '../components/ui';
import { useConfirm } from '../context/ConfirmContext';
import { SEASON_LABELS, STYLE_LABELS } from '../utils/labels';

const EMPTY_FILTERS = { keyword: '', season: '', style: '', favorite: '' };

export default function OutfitsPage() {
  const confirm = useConfirm();
  const navigate = useNavigate();
  const [outfits, setOutfits] = useState(null);
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [error, setError] = useState(null);
  const [triedIds, setTriedIds] = useState(() => new Set());
  const [modal, setModal] = useState(null);

  // Các outfit đã từng thử đồ (để hiện badge "🪞 Đã thử" trên card).
  useEffect(() => {
    tryOnApi
      .listTryOns()
      .then((results) => setTriedIds(new Set(results.map((r) => r.outfitId).filter(Boolean))))
      .catch(() => setTriedIds(new Set()));
  }, []);

  const load = useCallback(async (activeFilters) => {
    try {
      setOutfits(await outfitsApi.findOutfits(activeFilters));
    } catch (err) {
      setError(err.message);
    }
  }, []);

  useEffect(() => {
    const timer = setTimeout(() => load(filters), filters.keyword ? 300 : 0);
    return () => clearTimeout(timer);
  }, [filters, load]);

  const setFilter = (key) => (e) => setFilters((f) => ({ ...f, [key]: e.target.value }));

  const handleSave = async (payload) => {
    if (modal?.outfit) {
      await outfitsApi.updateOutfit(modal.outfit.id, payload);
    } else {
      await outfitsApi.createOutfit(payload);
    }
    setModal(null);
    await load(filters);
  };

  const handleDelete = async (outfit) => {
    const ok = await confirm({
      title: 'Xóa outfit?',
      message: `"${outfit.name}" sẽ bị xóa, kèm theo các kế hoạch trong lịch đang dùng outfit này.`,
      confirmLabel: '🗑️ Xóa luôn',
      danger: true,
    });
    if (!ok) return;
    try {
      await outfitsApi.deleteOutfit(outfit.id);
      await load(filters);
    } catch (err) {
      setError(err.message);
    }
  };

  const handleToggleFavorite = async (outfit) => {
    try {
      const updated = await outfitsApi.setOutfitFavorite(outfit.id, !outfit.favorite);
      setOutfits((list) => list.map((o) => (o.id === updated.id ? updated : o)));
    } catch (err) {
      setError(err.message);
    }
  };

  const handleWear = async (outfit) => {
    try {
      const updated = await outfitsApi.markOutfitWorn(outfit.id);
      setOutfits((list) => list.map((o) => (o.id === updated.id ? updated : o)));
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title tilt-right">🧢 Outfit</h1>
        <Button variant="pink" onClick={() => setModal({})}>
          ➕ Tạo outfit
        </Button>
      </div>

      <ErrorBanner error={error} onDismiss={() => setError(null)} />

      <div className="filter-bar">
        <Field label="Tìm kiếm" className="nb-field--grow">
          <input
            className="nb-input"
            value={filters.keyword}
            onChange={setFilter('keyword')}
            placeholder="Tìm theo tên hoặc mô tả..."
          />
        </Field>
        <Field label="Mùa">
          <select className="nb-select" value={filters.season} onChange={setFilter('season')}>
            <option value="">Tất cả</option>
            {Object.entries(SEASON_LABELS).map(([value, text]) => (
              <option key={value} value={value}>{text}</option>
            ))}
          </select>
        </Field>
        <Field label="Phong cách">
          <select className="nb-select" value={filters.style} onChange={setFilter('style')}>
            <option value="">Tất cả</option>
            {Object.entries(STYLE_LABELS).map(([value, text]) => (
              <option key={value} value={value}>{text}</option>
            ))}
          </select>
        </Field>
        <Field label="Yêu thích">
          <select className="nb-select" value={filters.favorite} onChange={setFilter('favorite')}>
            <option value="">Tất cả</option>
            <option value="true">⭐ Yêu thích</option>
            <option value="false">Chưa yêu thích</option>
          </select>
        </Field>
        <Button className="filter-reset" onClick={() => setFilters(EMPTY_FILTERS)}>
          🔄 Xóa lọc
        </Button>
      </div>

      {outfits === null ? (
        <Loading />
      ) : outfits.length === 0 ? (
        <EmptyState emoji="🧢">
          Chưa có outfit nào. Hãy phối những món đồ trong tủ thành một bộ hoàn chỉnh!
        </EmptyState>
      ) : (
        (() => {
          // Bộ thiếu món (do có món bị ẩn) tách xuống dưới thay vì biến mất — người dùng phải
          // thấy được nó để vào sửa, đó là cách duy nhất đưa bộ về trạng thái dùng lại được.
          const usable = outfits.filter((o) => o.available !== false);
          const broken = outfits.filter((o) => o.available === false);

          const renderCard = (outfit) => (
            <OutfitCard
              key={outfit.id}
              outfit={outfit}
              tried={triedIds.has(outfit.id)}
              onOpen={(o) => navigate(`/outfits/${o.id}`)}
              onEdit={(o) => setModal({ outfit: o })}
              onDelete={handleDelete}
              onToggleFavorite={handleToggleFavorite}
              onWear={handleWear}
            />
          );

          return (
            <>
              {usable.length > 0 ? (
                <div className="card-grid">{usable.map(renderCard)}</div>
              ) : (
                <EmptyState emoji="🧢">
                  Mọi outfit đang thiếu món. Hãy sửa các bộ bên dưới hoặc khôi phục món đã ẩn.
                </EmptyState>
              )}

              {broken.length > 0 && (
                <div className="unavailable-section">
                  <h2 className="unavailable-title">⚠️ Không khả dụng ({broken.length})</h2>
                  <p className="unavailable-note">
                    Các bộ này có món đã bị ẩn khỏi tủ đồ nên chưa mặc hay lên lịch được. Bấm
                    <strong> ✏️ Sửa</strong> rồi thay bằng món đang có để dùng lại.
                  </p>
                  <div className="card-grid">{broken.map(renderCard)}</div>
                </div>
              )}
            </>
          );
        })()
      )}

      {modal && (
        <OutfitFormModal outfit={modal.outfit} onSave={handleSave} onClose={() => setModal(null)} />
      )}
    </div>
  );
}
