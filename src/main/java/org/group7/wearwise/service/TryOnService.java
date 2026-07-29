package org.group7.wearwise.service;

import org.group7.wearwise.dto.response.TryOnProfileResponse;
import org.group7.wearwise.entity.AppUser;
import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.entity.Outfit;
import org.group7.wearwise.entity.TryOnResult;
import org.group7.wearwise.exception.AuthenticationFailedException;
import org.group7.wearwise.exception.ClothingItemNotFoundException;
import org.group7.wearwise.exception.OutfitNotFoundException;
import org.group7.wearwise.exception.TryOnImageException;
import org.group7.wearwise.exception.TryOnResultNotFoundException;
import org.group7.wearwise.exception.TryOnUnavailableException;
import org.group7.wearwise.repository.AppUserRepository;
import org.group7.wearwise.repository.ClothingItemRepository;
import org.group7.wearwise.repository.OutfitRepository;
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

    /**
     * Ràng buộc riêng của ảnh người: mỗi cạnh ≥ 300px, tỉ lệ trong khoảng 1:3 .. 3:1.
     * Định dạng, dung lượng và trần độ phân giải nằm ở {@link ImageValidator}.
     */
    private static final int MIN_DIMENSION = 300;
    private static final double MAX_ASPECT_RATIO = 3.0;

    private final AppUserRepository appUserRepository;
    private final ClothingItemRepository clothingItemRepository;
    private final OutfitRepository outfitRepository;
    private final TryOnResultRepository tryOnResultRepository;
    private final CloudinaryService cloudinaryService;
    private final TryOnApiClient tryOnApiClient;
    private final ImageValidator imageValidator;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public TryOnService(
            AppUserRepository appUserRepository,
            ClothingItemRepository clothingItemRepository,
            OutfitRepository outfitRepository,
            TryOnResultRepository tryOnResultRepository,
            CloudinaryService cloudinaryService,
            TryOnApiClient tryOnApiClient,
            ImageValidator imageValidator
    ) {
        this.appUserRepository = appUserRepository;
        this.clothingItemRepository = clothingItemRepository;
        this.outfitRepository = outfitRepository;
        this.tryOnResultRepository = tryOnResultRepository;
        this.cloudinaryService = cloudinaryService;
        this.tryOnApiClient = tryOnApiClient;
        this.imageValidator = imageValidator;
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

    /**
     * Ghép một món đồ lên ảnh người dùng.
     *
     * @param baseResultId khác NULL thì ghép chồng lên ảnh kết quả đó thay vì ảnh cơ thể gốc —
     *                     cách để mặc lần lượt quần rồi áo lên cùng một người. Kết quả trước phải
     *                     thuộc về chính người dùng đang gọi.
     */
    @Transactional
    public TryOnResult generateForItem(String username, Long itemId, Long baseResultId) {
        AppUser user = getUser(username);
        requireConfigured();

        String baseImageUrl = resolveBaseImage(user, baseResultId);

        ClothingItem item = clothingItemRepository.findByIdAndOwner_Username(itemId, user.getUsername())
                .orElseThrow(() -> new ClothingItemNotFoundException(itemId));

        if (item.getImageUrl() == null || item.getImageUrl().isBlank()) {
            throw new TryOnImageException("Món đồ này chưa có ảnh minh họa. Hãy thêm ảnh cho món đồ trước khi thử.");
        }

        // 1. Dịch vụ thử đồ ghép trang phục lên ảnh nền.
        String resultUrl = tryOnApiClient.generateTryOn(baseImageUrl, item.getImageUrl());

        // 2. Tải ảnh kết quả rồi lưu vĩnh viễn lên Cloudinary (URL của nhà cung cấp có thể hết hạn).
        String storedUrl = storeResult(resultUrl);

        // 3. Lưu bản ghi (chỉ URL).
        TryOnResult result = TryOnResult.builder()
                .owner(user)
                .clothingItemId(item.getId())
                .clothingItemName(item.getName())
                .garmentImageUrl(item.getImageUrl())
                .resultImageUrl(storedUrl)
                .baseImageUrl(baseImageUrl)
                .baseResultId(baseResultId)
                .build();

        return tryOnResultRepository.save(result);
    }

    /**
     * Thử nguyên một outfit: ghép tất cả món có ảnh trong outfit lên ảnh người dùng.
     *
     * <p>Gửi cả bộ trong <b>một</b> lời gọi thay vì ghép lần lượt từng món: mỗi lần ghép là một
     * lần model vẽ lại toàn bộ ảnh, nên ghép nối tiếp sẽ khiến khuôn mặt và dáng người trôi dần
     * sau mỗi lớp. Đổi lại, chất lượng phụ thuộc vào việc nhà cung cấp có xử lý tốt nhiều món
     * cùng lúc hay không — nếu kết quả kém, dùng chế độ mặc chồng lớp ở
     * {@link #generateForItem} để kiểm soát từng bước.</p>
     */
    @Transactional
    public TryOnResult generateForOutfit(String username, Long outfitId, Long baseResultId) {
        AppUser user = getUser(username);
        requireConfigured();

        String baseImageUrl = resolveBaseImage(user, baseResultId);

        Outfit outfit = outfitRepository.findByIdAndOwner_Username(outfitId, user.getUsername())
                .orElseThrow(() -> new OutfitNotFoundException(outfitId));

        List<String> garmentUrls = outfit.getClothingItems().stream()
                .map(ClothingItem::getImageUrl)
                .filter(url -> url != null && !url.isBlank())
                .toList();

        if (garmentUrls.isEmpty()) {
            throw new TryOnImageException(
                    "Outfit này chưa có món đồ nào có ảnh minh họa. Hãy thêm ảnh cho các món trong bộ trước khi thử.");
        }

        // 1. Ghép toàn bộ trang phục của outfit lên ảnh nền.
        String resultUrl = tryOnApiClient.generateTryOn(baseImageUrl, garmentUrls);

        // 2. Lưu vĩnh viễn lên Cloudinary (URL của nhà cung cấp có thể hết hạn).
        String storedUrl = storeResult(resultUrl);

        // 3. Lưu bản ghi gắn với outfit (chỉ URL).
        TryOnResult result = TryOnResult.builder()
                .owner(user)
                .outfitId(outfit.getId())
                .outfitName(outfit.getName())
                .garmentImageUrl(garmentUrls.get(0))
                .resultImageUrl(storedUrl)
                .baseImageUrl(baseImageUrl)
                .baseResultId(baseResultId)
                .build();

        return tryOnResultRepository.save(result);
    }

    private void requireConfigured() {
        if (!cloudinaryService.isConfigured() || !tryOnApiClient.isConfigured()) {
            throw new TryOnUnavailableException(
                    "Tính năng thử đồ ảo chưa được cấu hình. Vui lòng thêm API key của Cloudinary và tryon-api.com.");
        }
    }

    /**
     * Ảnh nền cho lần ghép: ảnh cơ thể của người dùng, hoặc ảnh kết quả trước đó khi mặc chồng lớp.
     *
     * <p>Bắt buộc lấy kết quả cũ qua {@code findByIdAndOwner_Username}: nếu tra theo mỗi id thì
     * người dùng A truyền id của người dùng B là ghép được đồ lên ảnh cơ thể của người khác.</p>
     */
    private String resolveBaseImage(AppUser user, Long baseResultId) {
        if (baseResultId != null) {
            TryOnResult previous = tryOnResultRepository
                    .findByIdAndOwner_Username(baseResultId, user.getUsername())
                    .orElseThrow(() -> new TryOnResultNotFoundException(baseResultId));
            return previous.getResultImageUrl();
        }

        String bodyPhotoUrl = user.getBodyPhotoUrl();
        if (bodyPhotoUrl == null || bodyPhotoUrl.isBlank()) {
            throw new TryOnImageException("Bạn cần tải ảnh của mình lên trước khi thử đồ.");
        }
        return bodyPhotoUrl;
    }

    /** URL của nhà cung cấp có thể hết hạn, nên tải về và lưu lại ngay trên Cloudinary. */
    private String storeResult(String providerImageUrl) {
        DownloadedImage downloaded = downloadImage(providerImageUrl);
        return cloudinaryService.uploadImage(downloaded.bytes(), downloaded.contentType(), "try-on");
    }

    @Transactional(readOnly = true)
    public List<TryOnResult> listResults(String username) {
        return tryOnResultRepository.findByOwner_UsernameOrderByCreatedAtDescIdDesc(normalizeUsername(username));
    }

    /** Lịch sử ảnh thử đồ của riêng một outfit (theo soft ref outfitId). */
    @Transactional(readOnly = true)
    public List<TryOnResult> listResultsForOutfit(String username, Long outfitId) {
        return tryOnResultRepository.findByOwner_UsernameAndOutfitIdOrderByCreatedAtDescIdDesc(
                normalizeUsername(username), outfitId);
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
        ImageValidator.ValidatedImage validated =
                imageValidator.read(file, "Vui lòng chọn một ảnh để tải lên.");

        int width = validated.width();
        int height = validated.height();

        if (Math.min(width, height) < MIN_DIMENSION) {
            throw new TryOnImageException(
                    "Ảnh quá nhỏ hoặc mờ. Cần ảnh rõ nét, mỗi cạnh tối thiểu " + MIN_DIMENSION + "px.");
        }

        double ratio = (double) Math.max(width, height) / Math.min(width, height);
        if (ratio > MAX_ASPECT_RATIO) {
            throw new TryOnImageException(
                    "Tỉ lệ ảnh không phù hợp. Hãy dùng ảnh chân dung/toàn thân với tỉ lệ trong khoảng 1:3 đến 3:1.");
        }

        return validated.bytes();
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
