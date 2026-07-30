package org.group7.wearwise.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Đọc và ghi CSV theo RFC 4180 (dấu phẩy, dấu ngoặc kép, {@code ""} là một dấu ngoặc kép).
 *
 * <p>Tự viết thay vì thêm thư viện: chỗ dùng duy nhất là nhập/xuất tủ đồ, và toàn bộ nghiệp vụ
 * gói trong ba quy tắc dưới đây. Một dependency mới còn phải theo dõi phiên bản và giấy phép.</p>
 *
 * <p>Ba cái bẫy đã xử lý sẵn, vì file người dùng nộp lên hầu như luôn đi qua Excel:</p>
 * <ul>
 *   <li><b>BOM</b> — Excel ghi UTF-8 kèm ba byte {@code EF BB BF} ở đầu file. Không cắt thì tên
 *       cột đầu tiên thành {@code "﻿name"} và không khớp header nào.</li>
 *   <li><b>Xuống dòng trong ô</b> — ghi chú nhiều dòng nằm trong cặp ngoặc kép; tách file bằng
 *       {@code split("\n")} là vỡ đúng ở những dòng đó.</li>
 *   <li><b>CRLF</b> — file từ Windows kết thúc dòng bằng {@code \r\n}; giữ lại {@code \r} thì mọi
 *       giá trị ở cột cuối mang thêm một ký tự vô hình, và so khớp enum thất bại.</li>
 * </ul>
 */
final class CsvCodec {

    /** Excel chỉ hiểu đúng tiếng Việt trong file UTF-8 khi có BOM dẫn đường. */
    static final String BOM = "﻿";

    private CsvCodec() {
    }

    /** @return danh sách dòng, mỗi dòng là danh sách ô. Dòng trắng hoàn toàn bị bỏ. */
    static List<List<String>> parse(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;

        String text = content.startsWith(BOM) ? content.substring(1) : content;

        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);

            if (quoted) {
                if (character != '"') {
                    cell.append(character);
                } else if (index + 1 < text.length() && text.charAt(index + 1) == '"') {
                    cell.append('"');
                    index++;
                } else {
                    quoted = false;
                }
                continue;
            }

            switch (character) {
                case '"' -> quoted = true;
                case ',' -> {
                    row.add(cell.toString());
                    cell.setLength(0);
                }
                case '\r' -> {
                    // Bỏ qua: '\n' ngay sau đó mới là dấu kết thúc dòng.
                }
                case '\n' -> {
                    row.add(cell.toString());
                    cell.setLength(0);
                    addIfNotBlank(rows, row);
                    row = new ArrayList<>();
                }
                default -> cell.append(character);
            }
        }

        row.add(cell.toString());
        addIfNotBlank(rows, row);

        return rows;
    }

    private static void addIfNotBlank(List<List<String>> rows, List<String> row) {
        if (row.stream().anyMatch(value -> !value.isBlank())) {
            rows.add(List.copyOf(row));
        }
    }

    static void appendRow(StringBuilder out, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                out.append(',');
            }
            out.append(quote(values.get(index)));
        }
        // CRLF theo đúng RFC 4180: Excel bản Windows đọc file chỉ có LF thành một dòng duy nhất.
        out.append("\r\n");
    }

    /**
     * Chỉ bọc ngoặc kép khi cần. Bọc tất cả cũng đúng cú pháp nhưng file mở bằng Notepad thì rối
     * mắt, mà file mẫu là thứ người dùng phải đọc và sửa tay.
     */
    private static String quote(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuote = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0
                || !value.trim().equals(value);

        return needsQuote ? '"' + value.replace("\"", "\"\"") + '"' : value;
    }
}
