/** Cạnh dài tối đa khi gửi ảnh cho AI — khớp với MAX_IMAGE_EDGE bên ClothingItemVisionService. */
const MAX_EDGE = 768;

/**
 * Thu ảnh về tối đa {@link MAX_EDGE}px rồi mã hóa JPEG, dùng trước khi gửi cho AI nhận diện.
 *
 * Mục đích là băng thông chứ không phải token: Gemini tính ảnh một giá cố định bất kể kích
 * thước, nhưng ảnh chụp điện thoại 3-5MB tải lên bằng 4G thì chờ rất lâu — sau bước này còn
 * khoảng 20KB nên gần như tức thì.
 *
 * Backend vẫn thu nhỏ lại một lần nữa vì dữ liệu từ client không đáng tin.
 * Lỗi đọc/giải mã ảnh sẽ trả về chính file gốc để backend tự xử lý và báo lỗi cho tử tế.
 */
export function shrinkImageForAi(file) {
  return new Promise((resolve) => {
    const objectUrl = URL.createObjectURL(file);
    const image = new Image();

    image.onload = () => {
      URL.revokeObjectURL(objectUrl);

      const scale = Math.min(1, MAX_EDGE / Math.max(image.width, image.height));
      const width = Math.max(1, Math.round(image.width * scale));
      const height = Math.max(1, Math.round(image.height * scale));

      const canvas = document.createElement('canvas');
      canvas.width = width;
      canvas.height = height;

      const context = canvas.getContext('2d');
      // JPEG không có kênh alpha: nền trong suốt của PNG phải tô trắng, nếu không vùng đó
      // thành đen và AI hay đoán nhầm thành món đồ màu đen.
      context.fillStyle = '#ffffff';
      context.fillRect(0, 0, width, height);
      context.drawImage(image, 0, 0, width, height);

      canvas.toBlob(
        (blob) => resolve(blob ? new File([blob], 'item.jpg', { type: 'image/jpeg' }) : file),
        'image/jpeg',
        0.8
      );
    };

    image.onerror = () => {
      URL.revokeObjectURL(objectUrl);
      resolve(file);
    };

    image.src = objectUrl;
  });
}
