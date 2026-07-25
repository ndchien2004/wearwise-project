package org.group7.wearwise.service;

import org.group7.wearwise.dto.response.ClothingItemResponse;
import org.group7.wearwise.dto.response.OutfitResponse;
import org.group7.wearwise.dto.response.ShareImportResponse;
import org.group7.wearwise.dto.response.SharePreviewResponse;
import org.group7.wearwise.dto.response.ShareResponse;
import org.group7.wearwise.entity.AppUser;
import org.group7.wearwise.entity.ClothingItem;
import org.group7.wearwise.entity.Outfit;
import org.group7.wearwise.entity.Share;
import org.group7.wearwise.enums.ClothingCondition;
import org.group7.wearwise.enums.ClothingStatus;
import org.group7.wearwise.enums.ShareTargetType;
import org.group7.wearwise.exception.AuthenticationFailedException;
import org.group7.wearwise.exception.BusinessRuleException;
import org.group7.wearwise.exception.ErrorCode;
import org.group7.wearwise.exception.ClothingItemNotFoundException;
import org.group7.wearwise.exception.OutfitNotFoundException;
import org.group7.wearwise.exception.ShareNotFoundException;
import org.group7.wearwise.repository.AppUserRepository;
import org.group7.wearwise.repository.ClothingItemRepository;
import org.group7.wearwise.repository.OutfitRepository;
import org.group7.wearwise.repository.ShareRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Chia sẻ trang phục giữa hai tài khoản bằng mã ngắn: chủ sở hữu tạo mã, người nhận nhập mã
 * và chép về một bản sao độc lập trong tủ đồ của mình.
 */
@Service
public class ShareService {

    /** Bỏ 0/O/1/I/L để đọc và gõ lại mã không bị nhầm. */
    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final int MAX_CODE_ATTEMPTS = 12;

    private final ShareRepository shareRepository;
    private final OutfitRepository outfitRepository;
    private final ClothingItemRepository clothingItemRepository;
    private final AppUserRepository appUserRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public ShareService(
            ShareRepository shareRepository,
            OutfitRepository outfitRepository,
            ClothingItemRepository clothingItemRepository,
            AppUserRepository appUserRepository
    ) {
        this.shareRepository = shareRepository;
        this.outfitRepository = outfitRepository;
        this.clothingItemRepository = clothingItemRepository;
        this.appUserRepository = appUserRepository;
    }

    /**
     * Tạo mã chia sẻ cho một outfit hoặc món đồ của chính người gọi. Nếu trang phục đó đang
     * có sẵn một mã còn hiệu lực thì trả lại mã cũ — bấm "chia sẻ" nhiều lần không sinh rác.
     */
    @Transactional
    public ShareResponse createShare(
            String ownerUsername,
            ShareTargetType targetType,
            Long targetId,
            Integer expiresInDays
    ) {
        String username = requireUsername(ownerUsername);
        AppUser owner = appUserRepository.findByUsername(username)
                .orElseThrow(AuthenticationFailedException::new);
        LocalDateTime now = LocalDateTime.now();

        Optional<Share> existing = targetType == ShareTargetType.OUTFIT
                ? shareRepository.findFirstByOwner_UsernameAndOutfit_IdAndRevokedAtIsNullOrderByIdDesc(username, targetId)
                : shareRepository.findFirstByOwner_UsernameAndClothingItem_IdAndRevokedAtIsNullOrderByIdDesc(username, targetId);

        Optional<Share> reusable = existing.filter(share -> share.isUsable(now));
        if (reusable.isPresent()) {
            return ShareResponse.from(reusable.get(), now);
        }

        Share.ShareBuilder builder = Share.builder()
                .code(generateUniqueCode())
                .targetType(targetType)
                .owner(owner)
                .expiresAt(expiresInDays == null ? null : now.plusDays(expiresInDays));

        if (targetType == ShareTargetType.OUTFIT) {
            Outfit outfit = outfitRepository.findByIdAndOwner_Username(targetId, username)
                    .orElseThrow(() -> new OutfitNotFoundException(targetId));
            assertShareable(outfit);
            builder.outfit(outfit);
        } else {
            ClothingItem item = clothingItemRepository.findByIdAndOwner_Username(targetId, username)
                    .orElseThrow(() -> new ClothingItemNotFoundException(targetId));
            assertShareable(item);
            builder.clothingItem(item);
        }

        return ShareResponse.from(shareRepository.save(builder.build()), now);
    }

    @Transactional(readOnly = true)
    public List<ShareResponse> listMyShares(String ownerUsername) {
        LocalDateTime now = LocalDateTime.now();
        return shareRepository.findByOwner_UsernameOrderByCreatedAtDescIdDesc(requireUsername(ownerUsername))
                .stream()
                .map(share -> ShareResponse.from(share, now))
                .toList();
    }

    /** Thu hồi mã. Mã đã thu hồi không dùng lại được, nhưng bản sao người khác đã chép vẫn còn. */
    @Transactional
    public void revokeShare(String ownerUsername, String code) {
        String username = requireUsername(ownerUsername);
        Share share = shareRepository.findByCode(normalizeCode(code))
                .filter(candidate -> candidate.getOwner().getUsername().equals(username))
                .orElseThrow(() -> ShareNotFoundException.forCode(code));

        if (share.getRevokedAt() == null) {
            share.setRevokedAt(LocalDateTime.now());
            shareRepository.save(share);
        }
    }

    @Transactional(readOnly = true)
    public SharePreviewResponse previewShare(String viewerUsername, String code) {
        return SharePreviewResponse.from(getUsableShare(code), requireUsername(viewerUsername));
    }

    /**
     * Chép nội dung của mã về tủ đồ người gọi. Bản sao luôn bắt đầu "sạch": chưa mặc lần nào,
     * trạng thái dùng được, không đánh dấu yêu thích.
     */
    @Transactional
    public ShareImportResponse importShare(String importerUsername, String code) {
        String username = requireUsername(importerUsername);
        Share share = getUsableShare(code);

        if (share.getOwner().getUsername().equals(username)) {
            throw new BusinessRuleException(ErrorCode.SHARE_OWN_CODE, "Đây là mã chia sẻ của chính bạn, không cần chép lại.");
        }

        // Chủ mã có thể đã ẩn món sau khi chia sẻ — chặn để người nhận không chép về bộ thiếu món.
        if (share.getTargetType() == ShareTargetType.OUTFIT) {
            assertShareable(share.getOutfit());
        } else {
            assertShareable(share.getClothingItem());
        }

        AppUser importer = appUserRepository.findByUsername(username)
                .orElseThrow(AuthenticationFailedException::new);

        share.setImportCount(share.getImportCount() + 1);
        shareRepository.save(share);

        return share.getTargetType() == ShareTargetType.OUTFIT
                ? importOutfit(share.getOutfit(), importer)
                : importItem(share.getClothingItem(), importer);
    }

    /** Không chia sẻ thứ mà chính chủ cũng không mặc được — người nhận sẽ chép về một bộ thiếu món. */
    private void assertShareable(Outfit outfit) {
        if (!OutfitService.isAvailable(outfit)) {
            throw new BusinessRuleException(
                    ErrorCode.SHARE_TARGET_UNAVAILABLE,
                    "Outfit \"" + outfit.getName() + "\" đang thiếu món do có món đã bị ẩn nên chưa chia sẻ được. "
                            + "Hãy sửa outfit trước."
            );
        }
    }

    private void assertShareable(ClothingItem item) {
        if (item.getArchivedAt() != null) {
            throw new BusinessRuleException(
                    ErrorCode.SHARE_TARGET_UNAVAILABLE,
                    "\"" + item.getName() + "\" đã bị ẩn khỏi tủ đồ nên chưa chia sẻ được. Hãy khôi phục món này trước."
            );
        }
    }

    private ShareImportResponse importOutfit(Outfit source, AppUser importer) {
        Set<ClothingItem> copiedItems = new LinkedHashSet<>();
        int created = 0;

        for (ClothingItem sourceItem : source.getClothingItems()) {
            Optional<ClothingItem> existing = findExistingCopy(importer.getUsername(), sourceItem);
            if (existing.isPresent()) {
                copiedItems.add(existing.get());
            } else {
                copiedItems.add(clothingItemRepository.save(copyItem(sourceItem, importer)));
                created++;
            }
        }

        Outfit copy = Outfit.builder()
                .name(source.getName())
                .description(source.getDescription())
                .imageUrl(source.getImageUrl())
                .season(source.getSeason())
                .style(source.getStyle())
                .favorite(false)
                .wearCount(0)
                .owner(importer)
                .clothingItems(copiedItems)
                .build();

        Outfit saved = outfitRepository.save(copy);
        int reused = copiedItems.size() - created;

        return new ShareImportResponse(
                ShareTargetType.OUTFIT,
                OutfitResponse.from(saved),
                null,
                created,
                reused,
                "Đã thêm outfit \"" + saved.getName() + "\" vào tủ đồ của bạn."
        );
    }

    private ShareImportResponse importItem(ClothingItem source, AppUser importer) {
        Optional<ClothingItem> existing = findExistingCopy(importer.getUsername(), source);

        if (existing.isPresent()) {
            return new ShareImportResponse(
                    ShareTargetType.CLOTHING_ITEM,
                    null,
                    ClothingItemResponse.from(existing.get()),
                    0,
                    1,
                    "\"" + existing.get().getName() + "\" đã có sẵn trong tủ đồ của bạn."
            );
        }

        ClothingItem saved = clothingItemRepository.save(copyItem(source, importer));

        return new ShareImportResponse(
                ShareTargetType.CLOTHING_ITEM,
                null,
                ClothingItemResponse.from(saved),
                1,
                0,
                "Đã thêm \"" + saved.getName() + "\" vào tủ đồ của bạn."
        );
    }

    /**
     * Món coi là "đã có" khi trùng cả tên, loại và ảnh — dấu hiệu của một bản chép trước đó.
     * Nhờ vậy chép cùng một mã hai lần không làm tủ đồ đầy bản sao trùng nhau.
     */
    private Optional<ClothingItem> findExistingCopy(String ownerUsername, ClothingItem source) {
        return clothingItemRepository
                .findByOwner_UsernameAndNameIgnoreCaseAndCategoryAndArchivedAtIsNull(ownerUsername, source.getName(), source.getCategory())
                .stream()
                .filter(candidate -> Objects.equals(candidate.getImageUrl(), source.getImageUrl()))
                .findFirst();
    }

    private ClothingItem copyItem(ClothingItem source, AppUser owner) {
        return ClothingItem.builder()
                .name(source.getName())
                .color(source.getColor())
                .colorTone(source.getColorTone())
                .category(source.getCategory())
                .season(source.getSeason())
                .style(source.getStyle())
                .condition(ClothingCondition.GOOD)
                .status(ClothingStatus.AVAILABLE)
                .wearCount(0)
                .favorite(false)
                .imageUrl(source.getImageUrl())
                .owner(owner)
                .build();
    }

    private Share getUsableShare(String code) {
        Share share = shareRepository.findByCode(normalizeCode(code))
                .orElseThrow(() -> ShareNotFoundException.forCode(code));

        if (share.getRevokedAt() != null) {
            throw ShareNotFoundException.revoked();
        }

        if (!share.isUsable(LocalDateTime.now())) {
            throw ShareNotFoundException.expired();
        }

        return share;
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            StringBuilder code = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                code.append(CODE_ALPHABET.charAt(secureRandom.nextInt(CODE_ALPHABET.length())));
            }

            String candidate = code.toString();
            if (!shareRepository.existsByCode(candidate)) {
                return candidate;
            }
        }

        throw new IllegalStateException("Unable to generate a unique share code.");
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new ShareNotFoundException(ErrorCode.SHARE_NOT_FOUND, "Mã chia sẻ không được để trống.");
        }

        // Người dùng hay dán cả link: chỉ lấy đoạn cuối, bỏ khoảng trắng và gạch nối.
        String trimmed = code.trim();
        int lastSlash = trimmed.lastIndexOf('/');
        if (lastSlash >= 0) {
            trimmed = trimmed.substring(lastSlash + 1);
        }

        return trimmed.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private String requireUsername(String username) {
        if (username == null || username.trim().isBlank()) {
            throw new AuthenticationFailedException();
        }

        return username.trim();
    }
}
