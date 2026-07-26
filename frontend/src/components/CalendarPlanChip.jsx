import { OutfitPreviewChip } from './WearPreview';

/**
 * Chip tên outfit trong ô ngày của lịch. Hover (hoặc focus) sẽ mở thẻ xem nhanh bộ đồ —
 * cùng thẻ mà trang chủ dùng cho các bảng thống kê.
 */
export default function CalendarPlanChip({ plan }) {
  return (
    <OutfitPreviewChip
      outfit={plan.outfit}
      meta={plan.completed ? '✅ Đã mặc' : '🕐 Chưa mặc'}
      note={plan.note}
      className={`calendar-plan-chip ${plan.completed ? 'is-done' : ''}`}
    />
  );
}
