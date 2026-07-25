package org.group7.wearwise.controller;

import jakarta.validation.Valid;
import org.group7.wearwise.dto.request.CreateShareRequest;
import org.group7.wearwise.dto.response.ShareImportResponse;
import org.group7.wearwise.dto.response.SharePreviewResponse;
import org.group7.wearwise.dto.response.ShareResponse;
import org.group7.wearwise.service.ShareService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/shares")
public class ShareController {

    private final ShareService shareService;

    public ShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShareResponse createShare(Authentication authentication, @Valid @RequestBody CreateShareRequest request) {
        return shareService.createShare(
                authentication.getName(),
                request.targetType(),
                request.targetId(),
                request.expiresInDays()
        );
    }

    /** Danh sách mã do chính người dùng tạo, để xem lại hoặc thu hồi. */
    @GetMapping
    public List<ShareResponse> listMyShares(Authentication authentication) {
        return shareService.listMyShares(authentication.getName());
    }

    @GetMapping("/{code}")
    public SharePreviewResponse previewShare(Authentication authentication, @PathVariable String code) {
        return shareService.previewShare(authentication.getName(), code);
    }

    @PostMapping("/{code}/import")
    @ResponseStatus(HttpStatus.CREATED)
    public ShareImportResponse importShare(Authentication authentication, @PathVariable String code) {
        return shareService.importShare(authentication.getName(), code);
    }

    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeShare(Authentication authentication, @PathVariable String code) {
        shareService.revokeShare(authentication.getName(), code);
    }
}
