import { Component } from 'react';

/**
 * Chặn lỗi render để một component hỏng không kéo cả ứng dụng thành màn hình trắng.
 *
 * React mặc định gỡ bỏ toàn bộ cây giao diện khi có lỗi chưa được bắt trong lúc render — người
 * dùng chỉ thấy nền trắng, không thông báo, không nút nào bấm được, và cũng không có manh mối gì
 * để báo lỗi. Ở đây ta thay bằng một màn hình có nội dung: nói rõ đã có sự cố, cho đường thoát,
 * và hiện thông điệp lỗi để người dùng chụp màn hình gửi lại.
 *
 * <p>Đây là lưới an toàn, không phải chỗ để xử lý lỗi. Lỗi nghiệp vụ (gọi API thất bại, dữ liệu
 * không hợp lệ) phải được bắt ngay tại nơi phát sinh và hiển thị bằng `ErrorBanner`.
 *
 * <p>Phải là class component: React chưa có API tương đương cho hàm.
 */
export default class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { error: null };
  }

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidCatch(error, info) {
    // Giữ lại vết trong console để còn lần ra nguyên nhân khi người dùng báo lỗi.
    console.error('Giao diện gặp lỗi không xử lý được:', error, info?.componentStack);
  }

  render() {
    const { error } = this.state;
    if (!error) {
      return this.props.children;
    }

    return (
      <div className="crash-screen">
        <div className="nb-card crash-card">
          <span className="crash-emoji" aria-hidden="true">
            🧦
          </span>
          <h1 className="crash-title">Ối, giao diện bị vấp</h1>
          <p className="crash-text">
            Có lỗi ngoài dự tính khi hiển thị trang này. Dữ liệu của bạn vẫn an toàn — chưa có gì
            bị mất.
          </p>

          <code className="crash-detail">{error.message || String(error)}</code>

          <div className="crash-actions">
            <button type="button" className="nb-btn nb-btn--primary" onClick={() => window.location.reload()}>
              ↻ Tải lại trang
            </button>
            <a className="nb-btn" href="/">
              🏠 Về trang chủ
            </a>
          </div>
        </div>
      </div>
    );
  }
}
