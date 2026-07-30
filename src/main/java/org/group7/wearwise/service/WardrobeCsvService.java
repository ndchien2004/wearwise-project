package org.group7.wearwise.service;

import org.group7.wearwise.dto.response.WardrobeImportResponse;
import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.ClothingCondition;
import org.group7.wearwise.enums.ClothingStatus;
import org.group7.wearwise.enums.ColorTone;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.group7.wearwise.exception.BusinessRuleException;
import org.group7.wearwise.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Nhập / xuất toàn bộ tủ đồ bằng CSV.
 *
 * <p>Chọn CSV chứ không phải JSON vì đây là định dạng người dùng <b>sửa được</b>: mở bằng Excel hay
 * Google Sheets, dán 200 dòng từ file kiểm kê cũ, rồi nộp lên. Một file JSON đúng cấu trúc thì chỉ
 * lập trình viên soạn nổi.</p>
 *
 * <p><b>Nhập theo từng dòng, không phải cả lô.</b> Khác {@code ClothingItemService.createItems}
 * (quét ảnh hàng loạt, tối đa 12 món, một món sai là hủy cả lô): ở đây một file 300 dòng mà hủy hết
 * vì dòng 217 gõ sai tên mùa là bắt người dùng dò lại từ đầu. Dòng hợp lệ vào tủ, dòng sai được báo
 * kèm số dòng, nộp lại file đã sửa thì phần đã vào được bỏ qua nhờ luật trùng tên.</p>
 *
 * <p>Vì vậy service này <b>không</b> mang {@code @Transactional}: mỗi dòng là một giao dịch riêng
 * do {@code ClothingItemService.createItem} mở. Bọc cả vòng lặp trong một giao dịch thì một dòng
 * sai sẽ đánh dấu giao dịch rollback-only, và mọi dòng hợp lệ trước đó lặng lẽ mất lúc commit —
 * cùng cái bẫy proxy mô tả ở mục 4.7 của CLAUDE.md.</p>
 */
@Service
public class WardrobeCsvService {

    private static final Logger log = LoggerFactory.getLogger(WardrobeCsvService.class);

    /**
     * Trần số dòng mỗi lần nhập. Mỗi dòng là một giao dịch riêng nên file càng dài càng giữ kết nối
     * lâu; 500 món đã lớn hơn tủ đồ thật của gần như mọi người dùng.
     */
    public static final int MAX_IMPORT_ROWS = 500;

    /** Trần kích thước file. CSV 500 dòng chưa tới 100KB, nên 1MB là đã rất rộng tay. */
    private static final long MAX_FILE_BYTES = 1_000_000L;

    /** Thứ tự cột khi xuất, cũng là thứ tự cột trong file mẫu. */
    private static final List<String> COLUMNS = List.of(
            "name", "category", "color", "colorTone", "season", "style",
            "condition", "status", "wearCount", "lastWornAt", "favorite", "imageUrl");

    /** Thiếu một trong bốn cột này thì không dựng được món đồ, nên từ chối cả file ngay. */
    private static final List<String> REQUIRED_COLUMNS = List.of("name", "category", "season", "style");

    private final ClothingItemService clothingItemService;

    public WardrobeCsvService(ClothingItemService clothingItemService) {
        this.clothingItemService = clothingItemService;
    }

    // ---------------------------------------------------------------- xuất

    /** Toàn bộ món chưa bị ẩn của người dùng. Món đã ẩn cố tình không xuất — chúng đã ra khỏi tủ. */
    public String export(String username) {
        StringBuilder out = new StringBuilder(CsvCodec.BOM);
        CsvCodec.appendRow(out, COLUMNS);

        for (ClothingItem item : clothingItemService.getAllItems(username)) {
            CsvCodec.appendRow(out, List.of(
                    nullToEmpty(item.getName()),
                    name(item.getCategory()),
                    nullToEmpty(item.getColor()),
                    name(item.getColorTone()),
                    name(item.getSeason()),
                    name(item.getStyle()),
                    name(item.getCondition()),
                    name(item.getStatus()),
                    item.getWearCount() == null ? "0" : String.valueOf(item.getWearCount()),
                    item.getLastWornAt() == null ? "" : item.getLastWornAt().toLocalDate().toString(),
                    Boolean.TRUE.equals(item.getFavorite()) ? "true" : "false",
                    nullToEmpty(item.getImageUrl())
            ));
        }

        return out.toString();
    }

    /**
     * File mẫu: header, hai dòng ví dụ, rồi một khối liệt kê giá trị hợp lệ của từng cột.
     *
     * <p>Khối liệt kê nằm ngay trong file thay vì chỉ ở tài liệu, vì người dùng sửa file bằng Excel
     * chứ không mở README. Mỗi dòng ghi chú bắt đầu bằng {@code #} và bị bỏ qua khi nhập.</p>
     */
    public String template() {
        StringBuilder out = new StringBuilder(CsvCodec.BOM);
        CsvCodec.appendRow(out, COLUMNS);

        CsvCodec.appendRow(out, List.of(
                "Áo sơ mi trắng", "SHIRT", "Trắng", "NEUTRAL", "ALL_SEASON", "FORMAL",
                "GOOD", "AVAILABLE", "0", "", "false", ""));
        CsvCodec.appendRow(out, List.of(
                "Quần jean xanh", "PANTS", "Xanh đậm", "COOL", "ALL_SEASON", "CASUAL",
                "GOOD", "AVAILABLE", "12", LocalDate.now().minusDays(3).toString(), "true", ""));

        out.append("\r\n");
        comment(out, "Xóa hai dòng ví dụ ở trên rồi điền tủ đồ của bạn. Dòng bắt đầu bằng # bị bỏ qua.");
        comment(out, "Bắt buộc: name, category, season, style. Các cột còn lại để trống là dùng mặc định.");
        comment(out, "category: " + allowed(ClothingCategory.values()));
        comment(out, "season: " + allowed(Season.values()));
        comment(out, "style: " + allowed(Style.values()));
        comment(out, "colorTone (tùy chọn): " + allowed(ColorTone.values()));
        comment(out, "condition: " + allowed(ClothingCondition.values()) + " — mặc định GOOD");
        comment(out, "status: " + allowed(ClothingStatus.values()) + " — mặc định AVAILABLE");
        comment(out, "wearCount: số nguyên >= 0, mặc định 0");
        comment(out, "lastWornAt: ngày dạng YYYY-MM-DD, để trống nếu chưa mặc");
        comment(out, "favorite: true / false, mặc định false");
        comment(out, "imageUrl: để trống — ảnh tải lên ở trang Tủ đồ, không nhập bằng file này");

        return out.toString();
    }

    // ---------------------------------------------------------------- nhập

    public WardrobeImportResponse importCsv(String username, MultipartFile file, boolean skipDuplicateNames) {
        List<List<String>> rows = CsvCodec.parse(readText(file));
        if (rows.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.INVALID_REQUEST, "File rỗng — chưa có dòng nào để nhập.");
        }

        Map<String, Integer> columns = readHeader(rows.get(0));

        // Số dòng thật trong file, để báo lỗi theo đúng số dòng người dùng thấy trong Excel. Dòng
        // ghi chú và dòng trắng đã bị lọc nên không dùng chỉ số của mảng được.
        List<Row> data = new ArrayList<>();
        for (int index = 1; index < rows.size(); index++) {
            List<String> raw = rows.get(index);
            if (raw.get(0).startsWith("#")) {
                continue;
            }
            data.add(new Row(index + 1, raw));
        }

        if (data.size() > MAX_IMPORT_ROWS) {
            throw new BusinessRuleException(
                    ErrorCode.INVALID_REQUEST,
                    "File có " + data.size() + " dòng, mỗi lần chỉ nhập được tối đa " + MAX_IMPORT_ROWS
                            + " dòng. Hãy tách file ra nhiều phần.");
        }

        // Tên đã có trong tủ, đọc một lần. Tên vừa thêm trong chính lượt nhập này cũng được nạp vào
        // đây, nếu không thì một file chứa hai dòng cùng tên vẫn tạo ra hai món trùng nhau.
        Set<String> takenNames = new HashSet<>();
        clothingItemService.getAllItems(username)
                .forEach(item -> takenNames.add(item.getName().trim().toLowerCase(Locale.ROOT)));

        List<WardrobeImportResponse.Problem> problems = new ArrayList<>();
        int created = 0;
        int skipped = 0;

        for (Row row : data) {
            String name = row.get(columns, "name");

            // Ô bắt buộc để trống thì tầng này tự báo, không đợi createItem ném
            // "Name is required." rồi dịch lại: thông báo phải nói bằng tên CỘT trong file người
            // dùng đang sửa, giống cách parseEnum báo cho category / season / style.
            if (name.isBlank()) {
                problems.add(new WardrobeImportResponse.Problem(
                        row.line(), "", WardrobeImportResponse.Kind.INVALID,
                        "Cột name không được để trống."));
                continue;
            }

            if (skipDuplicateNames
                    && takenNames.contains(name.trim().toLowerCase(Locale.ROOT))) {
                skipped++;
                problems.add(new WardrobeImportResponse.Problem(
                        row.line(), name, WardrobeImportResponse.Kind.SKIPPED_DUPLICATE,
                        "Tủ đồ đã có món tên này."));
                continue;
            }

            try {
                clothingItemService.createItem(
                        username,
                        name,
                        row.get(columns, "color"),
                        parseEnum(ColorTone.class, row.get(columns, "colorTone"), "colorTone", false),
                        parseEnum(ClothingCategory.class, row.get(columns, "category"), "category", true),
                        parseEnum(Season.class, row.get(columns, "season"), "season", true),
                        parseEnum(Style.class, row.get(columns, "style"), "style", true),
                        defaultIfNull(parseEnum(ClothingCondition.class, row.get(columns, "condition"),
                                "condition", false), ClothingCondition.GOOD),
                        defaultIfNull(parseEnum(ClothingStatus.class, row.get(columns, "status"),
                                "status", false), ClothingStatus.AVAILABLE),
                        parseWearCount(row.get(columns, "wearCount")),
                        parseLastWornAt(row.get(columns, "lastWornAt")),
                        parseBoolean(row.get(columns, "favorite")),
                        row.get(columns, "imageUrl")
                );

                created++;
                takenNames.add(name.trim().toLowerCase(Locale.ROOT));
            } catch (IllegalArgumentException | BusinessRuleException exception) {
                // Dòng sai không được làm đổ cả file: ghi lại rồi đi tiếp. Chỉ bắt hai loại lỗi
                // "dữ liệu người dùng sai" — lỗi hạ tầng vẫn phải nổi lên chứ không bị đếm thành
                // một dòng xấu.
                problems.add(new WardrobeImportResponse.Problem(
                        row.line(), name, WardrobeImportResponse.Kind.INVALID,
                        friendlyMessage(exception)));
            }
        }

        int failed = (int) problems.stream()
                .filter(problem -> problem.kind() == WardrobeImportResponse.Kind.INVALID)
                .count();

        log.info("Nhập tủ đồ của {}: {} dòng, thêm {}, bỏ qua {}, lỗi {}",
                username, data.size(), created, skipped, failed);

        return new WardrobeImportResponse(data.size(), created, skipped, failed, List.copyOf(problems));
    }

    // ---------------------------------------------------------------- đọc file

    private String readText(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.INVALID_REQUEST, "Chưa chọn file CSV nào.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BusinessRuleException(
                    ErrorCode.INVALID_REQUEST,
                    "File quá lớn (tối đa " + (MAX_FILE_BYTES / 1000) + "KB). File CSV của tủ đồ lẽ ra rất nhẹ — "
                            + "kiểm tra lại xem có chọn nhầm file khác không.");
        }

        try {
            // Luôn đọc UTF-8. Excel bản cũ có thể ghi CSV theo bảng mã hệ thống, khi đó tiếng Việt
            // ra ký tự lạ — nhưng đoán bảng mã còn sai nhiều hơn, nên file mẫu ghi rõ phải là UTF-8.
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new BusinessRuleException(ErrorCode.INVALID_REQUEST, "Không đọc được file. Hãy thử tải lại.");
        }
    }

    /** @return tên cột (chữ thường) → chỉ số, nên thứ tự cột trong file muốn thế nào cũng được. */
    private Map<String, Integer> readHeader(List<String> header) {
        Map<String, Integer> columns = new HashMap<>();
        for (int index = 0; index < header.size(); index++) {
            String key = header.get(index).trim().toLowerCase(Locale.ROOT);
            if (!key.isEmpty()) {
                columns.putIfAbsent(key, index);
            }
        }

        List<String> missing = REQUIRED_COLUMNS.stream()
                .filter(column -> !columns.containsKey(column.toLowerCase(Locale.ROOT)))
                .toList();

        if (!missing.isEmpty()) {
            throw new BusinessRuleException(
                    ErrorCode.INVALID_REQUEST,
                    "File thiếu cột bắt buộc: " + String.join(", ", missing)
                            + ". Hãy tải file mẫu và điền theo đúng dòng tiêu đề của nó.");
        }

        return columns;
    }

    /** Một dòng dữ liệu kèm số dòng thật trong file. */
    private record Row(int line, List<String> values) {

        String get(Map<String, Integer> columns, String column) {
            Integer index = columns.get(column.toLowerCase(Locale.ROOT));
            if (index == null || index >= values.size()) {
                return "";
            }
            return values.get(index).trim();
        }
    }

    // ---------------------------------------------------------------- ép kiểu từng ô

    private <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String column, boolean required) {
        if (raw == null || raw.isBlank()) {
            if (required) {
                throw new IllegalArgumentException(
                        "Cột " + column + " bắt buộc phải có, giá trị hợp lệ: " + allowed(type.getEnumConstants()));
            }
            return null;
        }

        // Chấp nhận chữ thường và khoảng trắng thừa: người dùng gõ tay trong Excel, không copy hằng số.
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        for (E candidate : type.getEnumConstants()) {
            if (candidate.name().equals(normalized)) {
                return candidate;
            }
        }

        throw new IllegalArgumentException(
                "Cột " + column + " có giá trị \"" + raw + "\" không hợp lệ. Giá trị hợp lệ: "
                        + allowed(type.getEnumConstants()));
    }

    private Integer parseWearCount(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 0) {
                throw new IllegalArgumentException("Cột wearCount không được là số âm.");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Cột wearCount phải là số nguyên, đang là \"" + raw + "\".");
        }
    }

    /**
     * Nhận ngày {@code YYYY-MM-DD} và quy về đầu ngày. Chỉ lấy ngày vì đó là thứ duy nhất người dùng
     * còn nhớ khi kiểm kê lại tủ đồ; giờ phút chính xác thì họ bịa ra cũng vô nghĩa.
     */
    private LocalDateTime parseLastWornAt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String value = raw.trim();
            // Chấp nhận luôn cả dạng có giờ, vì file xuất ra từ hệ thống khác có thể mang theo.
            return value.length() > 10
                    ? LocalDateTime.parse(value.replace(' ', 'T'))
                    : LocalDate.parse(value).atStartOfDay();
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "Cột lastWornAt phải có dạng YYYY-MM-DD, đang là \"" + raw + "\".");
        }
    }

    private Boolean parseBoolean(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        // "x" và "1" vì đó là cách người ta tick một ô trong Excel.
        if (Set.of("true", "1", "x", "yes", "có").contains(value)) {
            return true;
        }
        if (Set.of("false", "0", "no", "không").contains(value)) {
            return false;
        }
        throw new IllegalArgumentException("Cột favorite phải là true hoặc false, đang là \"" + raw + "\".");
    }

    // ---------------------------------------------------------------- phụ trợ

    /**
     * Lời văn cho người dùng. Thông báo của {@code createItem} viết bằng tiếng Anh cho lập trình
     * viên ("Name must be at most 255 characters."); dịch trường hợp gặp thật, còn lại giữ nguyên
     * văn để một lỗi lạ không bị che thành câu chung chung.
     */
    private String friendlyMessage(RuntimeException exception) {
        String message = exception.getMessage() == null ? "Dữ liệu không hợp lệ." : exception.getMessage();

        if (message.startsWith("Name") && message.contains("must be at most")) {
            return "Tên món quá dài (tối đa 255 ký tự).";
        }
        return message;
    }

    private static <E extends Enum<E>> String allowed(E[] values) {
        Set<String> names = new LinkedHashSet<>();
        for (E value : values) {
            names.add(value.name());
        }
        return String.join(" / ", names);
    }

    private static void comment(StringBuilder out, String text) {
        CsvCodec.appendRow(out, List.of("# " + text));
    }

    private static String name(Enum<?> value) {
        return value == null ? "" : value.name();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static <T> T defaultIfNull(T value, T fallback) {
        return value == null ? fallback : value;
    }
}
