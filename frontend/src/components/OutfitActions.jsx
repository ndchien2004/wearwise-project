import { Button } from './ui';
import { isWornToday } from '../utils/date';

/**
 * Hai nút "Lên lịch" / "Mặc luôn" của một card gợi ý.
 *
 * Tách riêng vì tab "Bộ phù hợp" và tab "AI chọn giúp" ở trang Gợi ý dùng đúng cặp nút này — để
 * mỗi tab tự dựng lấy thì sớm muộn hai chỗ lệch nhau về nhãn hoặc về điều kiện khóa nút.
 */
export default function OutfitActions({ outfit, onPlan, onWear }) {
  return (
    <div className="card-actions">
      <Button size="sm" variant="primary" onClick={() => onPlan(outfit)}>
        📅 Lên lịch
      </Button>
      {isWornToday(outfit.lastWornAt) ? (
        <Button size="sm" disabled>
          Đã mặc
        </Button>
      ) : (
        <Button size="sm" variant="green" onClick={() => onWear(outfit)}>
          👣 Mặc luôn
        </Button>
      )}
    </div>
  );
}
