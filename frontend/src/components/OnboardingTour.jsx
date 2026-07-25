import { useEffect, useState } from 'react';

/**
 * Vòng hướng dẫn kiểu game hiện ngay sau khi tạo tài khoản: bấm Tiếp để đi lần lượt qua từng
 * màn giới thiệu tính năng, hoặc Bỏ qua bất cứ lúc nào. Chỉ là các slide giới thiệu — không
 * chạm vào dữ liệu, đóng lại là vào dùng app bình thường.
 */
const STEPS = [
  {
    emoji: '🎉',
    title: 'Chào mừng đến WearWise!',
    body: 'Tủ đồ thông minh giúp bạn quản lý quần áo, phối đồ và nhiều hơn nữa. Cùng dạo nhanh qua các tính năng nhé — bấm Tiếp để xem, hoặc Bỏ qua nếu muốn tự khám phá.',
  },
  {
    emoji: '🏠',
    title: 'Trang chủ',
    body: 'Nơi tổng quan nhanh về tủ đồ của bạn: số món đang có, hoạt động gần đây và lối tắt tới mọi thứ.',
  },
  {
    emoji: '👕',
    title: 'Tủ đồ & Outfit',
    body: 'Thêm từng món quần áo — chỉ cần chụp ảnh, AI tự điền thông tin. Rồi ghép chúng thành các bộ outfit ưng ý.',
  },
  {
    emoji: '🌦️',
    title: 'Gợi ý & Lịch',
    body: 'Nhận gợi ý phối đồ theo thời tiết hôm nay, và lên lịch mặc gì cho những ngày sắp tới.',
  },
  {
    emoji: '🪞',
    title: 'Thử đồ ảo',
    body: 'Tải ảnh của bạn lên rồi ướm thử một món hoặc cả bộ outfit ngay trên chính mình.',
  },
  {
    emoji: '🔗',
    title: 'Chia sẻ',
    body: 'Khoe những bộ đồ đẹp với bạn bè chỉ bằng một đường link.',
  },
  {
    emoji: '👤',
    title: 'Tài khoản & Thống kê',
    body: 'Đổi ảnh đại diện, đổi mật khẩu và xem những thống kê thú vị về tủ đồ của bạn.',
  },
  {
    emoji: '☰',
    title: 'Menu điều hướng',
    body: 'Bấm nút Menu ở góc trên bên trái để mở bảng điều hướng kiểu game và nhảy giữa các mục cực nhanh. Sẵn sàng chưa?',
  },
];

export default function OnboardingTour({ onClose }) {
  const [index, setIndex] = useState(0);
  const [dir, setDir] = useState(1);

  const isLast = index === STEPS.length - 1;
  const step = STEPS[index];

  const goNext = () => {
    if (isLast) {
      onClose();
    } else {
      setDir(1);
      setIndex((i) => i + 1);
    }
  };

  const goBack = () => {
    if (index > 0) {
      setDir(-1);
      setIndex((i) => i - 1);
    }
  };

  // Điều hướng bằng bàn phím. Không đặt deps để closure luôn bắt đúng bước hiện tại. Không bắt
  // Enter ở đây: nút đang focus đã tự xử lý Enter, bắt thêm sẽ nhảy 2 bước một lúc.
  useEffect(() => {
    const onKey = (e) => {
      if (e.key === 'Escape') onClose();
      else if (e.key === 'ArrowRight') goNext();
      else if (e.key === 'ArrowLeft') goBack();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  });

  // Khóa cuộn nền trong lúc hướng dẫn.
  useEffect(() => {
    const previous = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = previous;
    };
  }, []);

  return (
    <div className="tour-overlay" role="dialog" aria-modal="true" aria-label="Hướng dẫn sử dụng WearWise">
      <div className="tour-card">
        <button type="button" className="tour-skip" onClick={onClose}>
          Bỏ qua ✕
        </button>

        {/* key đổi theo bước để chạy lại hiệu ứng trượt mỗi lần chuyển màn. */}
        <div key={index} className={`tour-body ${dir > 0 ? 'is-from-right' : 'is-from-left'}`}>
          <div className="tour-emoji" aria-hidden="true">{step.emoji}</div>
          <h2 className="tour-title">{step.title}</h2>
          <p className="tour-text">{step.body}</p>
        </div>

        <div className="tour-dots" aria-hidden="true">
          {STEPS.map((s, i) => (
            <span
              key={s.title}
              className={`tour-dot ${i === index ? 'is-active' : ''} ${i < index ? 'is-done' : ''}`}
            />
          ))}
        </div>

        <div className="tour-actions">
          <button type="button" className="nb-btn" onClick={goBack} disabled={index === 0}>
            ← Trước
          </button>
          <span className="tour-count">{index + 1}/{STEPS.length}</span>
          <button type="button" className="nb-btn nb-btn--primary" onClick={goNext}>
            {isLast ? '🚀 Bắt đầu' : 'Tiếp →'}
          </button>
        </div>
      </div>
    </div>
  );
}
