package org.group7.wearwise.controller;

import org.group7.wearwise.dto.response.WardrobeImportResponse;
import org.group7.wearwise.service.WardrobeCsvService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * Nhập / xuất tủ đồ bằng CSV.
 *
 * <p>{@code /import} được {@code RateLimitFilter} xếp vào nhóm UPLOAD (80 lượt/giờ). Nó không gọi
 * dịch vụ trả tiền nào, nhưng một lời gọi có thể ghi tới 500 dòng vào database — để nó rơi vào
 * GENERAL nghĩa là 240 lượt mỗi phút, tức 120.000 lượt ghi/phút từ một tài khoản.</p>
 */
@RestController
@RequestMapping("/api/wardrobe")
public class WardrobeTransferController {

    private final WardrobeCsvService wardrobeCsvService;

    public WardrobeTransferController(WardrobeCsvService wardrobeCsvService) {
        this.wardrobeCsvService = wardrobeCsvService;
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(Authentication authentication) {
        return csv(
                wardrobeCsvService.export(authentication.getName()),
                "wearwise-tu-do-" + LocalDate.now() + ".csv");
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        return csv(wardrobeCsvService.template(), "wearwise-mau-tu-do.csv");
    }

    @PostMapping("/import")
    public WardrobeImportResponse importCsv(
            Authentication authentication,
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "skipDuplicateNames", defaultValue = "true") boolean skipDuplicateNames
    ) {
        return wardrobeCsvService.importCsv(authentication.getName(), file, skipDuplicateNames);
    }

    /**
     * Trả về mảng byte đã mã hóa UTF-8 thay vì {@code String}: Spring áp charset của
     * {@code text/csv} là ISO-8859-1 theo mặc định, và tên món tiếng Việt sẽ thành dấu hỏi.
     */
    private ResponseEntity<byte[]> csv(String content, String filename) {
        byte[] body = content.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(body);
    }
}
