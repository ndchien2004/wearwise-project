package org.group7.wearwise.service;

import org.group7.wearwise.dto.response.TryOnProfileResponse;
import org.group7.wearwise.entity.AppUser;
import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.entity.TryOnResult;
import org.group7.wearwise.exception.AuthenticationFailedException;
import org.group7.wearwise.exception.ClothingItemNotFoundException;
import org.group7.wearwise.exception.TryOnImageException;
import org.group7.wearwise.exception.TryOnResultNotFoundException;
import org.group7.wearwise.exception.TryOnUnavailableException;
import org.group7.wearwise.repository.AppUserRepository;
import org.group7.wearwise.repository.ClothingItemRepository;
import org.group7.wearwise.repository.TryOnResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Set;

@Service
public class TryOnService {

    /** Giới hạn ảnh người: ≤ 10MB, mỗi cạnh ≥ 300px, tỉ lệ trong khoảng 1:3 .. 3:1. */
    private static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024;
    private static final int MIN_DIMENSION = 300;
    private static final double MAX_ASPECT_RATIO = 3.0;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/jpg", "image/png");

    private final AppUserRepository appUserRepository;
    private final ClothingItemRepository clothingItemRepository;
    private final TryOnResultRepository tryOnResultRepository;
    private final CloudinaryService cloudinaryService;
    private final TryOnApiClient tryOnApiClient;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public TryOnService(
            AppUserRepository appUserRepository,
            ClothingItemRepository clothingItemRepository,
            TryOnResultRepository tryOnResultRepository,
            CloudinaryService cloudinaryService,
            TryOnApiClient tryOnApiClient
    ) {
        this.appUserRepository = appUserRepository;
        this.clothingItemRepository = clothingItemRepository;
        this.tryOnResultRepository = tryOnResultRepository;
        this.cloudinaryService = cloudinaryService;
        this.tryOnApiClient = tryOnApiClient;
    }

    @Transactional(readOnly = true)
    public TryOnProfileResponse getProfile(String username) {
        AppUser user = getUser(username);
        return TryOnProfileResponse.of(
                user.getBodyPhotoUrl(),
                cloudinaryService.isConfigured(),
                tryOnApiClient.isConfigured()
        );
    }

    @Transactional
    public TryOnProfileResponse uploadBodyPhoto(String username, MultipartFile file) {
        AppUser user = getUser(username);
        byte[] bytes = validateAndReadImage(file);

        String url = cloudinaryService.uploadImage(bytes, file.getContentType(), "body-photos");
        user.setBodyPhotoUrl(url);
        appUserRepository.save(user);

        return TryOnProfileResponse.of(url, cloudinaryService.isConfigured(), tryOnApiClient.isConfigured());
    }

    @Transactional
    public TryOnProfileResponse deleteBodyPhoto(String username) {
        AppUser user = getUser(username);
        user.setBodyPhotoUrl(null);
        appUserRepository.save(user);
        return TryOnProfileResponse.of(null, cloudinaryService.isConfigured(), tryOnApiClient.isConfigured());
    }

    @Transactional
    public TryOnResult generateForItem(String username, Long itemId) {
        AppUser user = getUser(username);

        if (!cloudinaryService.isConfigured() || !tryOnApiClient.isConfigured()) {
            throw new TryOnUnavailableException(
                    "Tính năng thử đồ ảo chưa được cấu hình. Vui lòng thêm API key của Cloudinary và tryon-api.com.");
        }

        String bodyPhotoUrl = user.getBodyPhotoUrl();
        if (bodyPhotoUrl == null || bodyPhotoUrl.isBlank()) {
            throw new TryOnImageException("Bạn cần tải ảnh của mình lên trước khi thử đồ.");
        }

        ClothingItem item = clothingItemRepository.findByIdAndOwner_Username(itemId, user.getUsername())
                .orElseThrow(() -> new ClothingItemNotFoundException(itemId));

        if (item.getImageUrl() == null || item.getImageUrl().isBlank()) {
            throw new TryOnImageException("Món đồ này chưa có ảnh minh họa. Hãy thêm ảnh cho món đồ trước khi thử.");
        }

        // 1. Dịch vụ thử đồ ghép trang phục lên ảnh người dùng.
        String resultUrl = tryOnApiClient.generateTryOn(bodyPhotoUrl, item.getImageUrl());

        // 2. Tải ảnh kết quả rồi lưu vĩnh viễn lên Cloudinary (URL của nhà cung cấp có thể hết hạn).
        DownloadedImage downloaded = downloadImage(resultUrl);
        String storedUrl = cloudinaryService.uploadImage(downloaded.bytes(), downloaded.contentType(), "try-on");

        // 3. Lưu bản ghi (chỉ URL).
        TryOnResult result = TryOnResult.builder()
                .owner(user)
                .clothingItemId(item.getId())
                .clothingItemName(item.getName())
                .garmentImageUrl(item.getImageUrl())
                .resultImageUrl(storedUrl)
                .build();

        return tryOnResultRepository.save(result);
    }

    @Transactional(readOnly = true)
    public List<TryOnResult> listResults(String username) {
        return tryOnResultRepository.findByOwner_UsernameOrderByCreatedAtDescIdDesc(normalizeUsername(username));
    }

    /** Lịch sử ảnh thử đồ của riêng một món đồ (theo soft ref clothingItemId). */
    @Transactional(readOnly = true)
    public List<TryOnResult> listResultsForItem(String username, Long itemId) {
        return tryOnResultRepository.findByOwner_UsernameAndClothingItemIdOrderByCreatedAtDescIdDesc(
                normalizeUsername(username), itemId);
    }

    @Transactional
    public void deleteResult(String username, Long id) {
        TryOnResult result = tryOnResultRepository.findByIdAndOwner_Username(id, normalizeUsername(username))
                .orElseThrow(() -> new TryOnResultNotFoundException(id));
        tryOnResultRepository.delete(result);
    }

    // ---------- helpers ----------

    private byte[] validateAndReadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new TryOnImageException("Vui lòng chọn một ảnh để tải lên.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new TryOnImageException("Ảnh phải ở định dạng JPG hoặc PNG.");
        }

        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new TryOnImageException("Ảnh quá lớn (tối đa 10MB). Vui lòng chọn ảnh nhẹ hơn.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new TryOnImageException("Không đọc được tệp ảnh. Vui lòng thử lại.");
        }

        if (bytes.length == 0) {
            throw new TryOnImageException("Tệp ảnh rỗng. Vui lòng chọn ảnh khác.");
        }

        BufferedImage image = decodeImage(bytes);
        if (image == null) {
            throw new TryOnImageException("Không đọc được ảnh. Hãy thử một ảnh JPG/PNG rõ nét khác.");
        }

        int width = image.getWidth();
        int height = image.getHeight();

        if (Math.min(width, height) < MIN_DIMENSION) {
            throw new TryOnImageException(
                    "Ảnh quá nhỏ hoặc mờ. Cần ảnh rõ nét, mỗi cạnh tối thiểu " + MIN_DIMENSION + "px.");
        }

        double ratio = (double) Math.max(width, height) / Math.min(width, height);
        if (ratio > MAX_ASPECT_RATIO) {
            throw new TryOnImageException(
                    "Tỉ lệ ảnh không phù hợp. Hãy dùng ảnh chân dung/toàn thân với tỉ lệ trong khoảng 1:3 đến 3:1.");
        }

        return bytes;
    }

    private BufferedImage decodeImage(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException exception) {
            return null;
        }
    }

    private DownloadedImage downloadImage(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw new TryOnUnavailableException(
                        "Không tải được ảnh kết quả từ dịch vụ thử đồ (HTTP " + response.statusCode() + ").");
            }
            byte[] body = response.body();
            if (body == null || body.length == 0) {
                throw new TryOnUnavailableException("Ảnh kết quả từ dịch vụ thử đồ rỗng.");
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("image/png");
            return new DownloadedImage(body, contentType);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new TryOnUnavailableException("Lỗi khi tải ảnh kết quả: " + exception.getMessage());
        }
    }

    private AppUser getUser(String username) {
        return appUserRepository.findByUsername(normalizeUsername(username))
                .orElseThrow(AuthenticationFailedException::new);
    }

    private String normalizeUsername(String username) {
        if (username == null || username.trim().isBlank()) {
            throw new AuthenticationFailedException();
        }
        return username.trim();
    }

    private record DownloadedImage(byte[] bytes, String contentType) {
    }
}
