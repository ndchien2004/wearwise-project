package org.group7.wearwise.enums;

/** Tone màu của món đồ — dùng để lọc tủ đồ và gợi ý phối màu. */
public enum ColorTone {
    WARM("Tông ấm"),
    COOL("Tông lạnh"),
    NEUTRAL("Trung tính"),
    PASTEL("Pastel"),
    BRIGHT("Rực rỡ"),
    DARK("Tông tối");

    private final String label;

    ColorTone(String label) {
        this.label = label;
    }

    /**
     * Tên tiếng Việt để ghép vào prompt gửi cho AI. Để ở enum vì có nhiều service cần dịch tone
     * sang chữ; mỗi nơi tự giữ một bảng thì thêm tone mới sẽ sót chỗ này chỗ kia.
     *
     * <p>Đây <b>không</b> phải nhãn hiển thị của giao diện — frontend có bảng riêng
     * ({@code TONE_LABELS}) vì chữ trên màn hình còn phụ thuộc chỗ trống và ngữ cảnh.</p>
     */
    public String getLabel() {
        return label;
    }
}
