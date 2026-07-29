package org.group7.wearwise.repository;

import org.group7.wearwise.entity.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Chỉ những tài khoản có hạn mức riêng — dùng để nạp {@code UserRateLimitOverrides} lúc khởi
     * động. Thường chỉ vài dòng nên không cần phân trang.
     */
    @Query("select user from AppUser user "
            + "where user.aiQuotaPerHour is not null or user.externalQuotaPerHour is not null")
    List<AppUser> findAllWithRateLimitOverride();

    /**
     * Danh sách tài khoản cho màn hình quản trị. {@code query} NULL thì trả về tất cả; ngược lại
     * lọc theo tên đăng nhập hoặc email.
     */
    @Query("""
            select user from AppUser user
            where :query is null
               or lower(user.username) like lower(concat('%', :query, '%'))
               or lower(user.email) like lower(concat('%', :query, '%'))
            """)
    Page<AppUser> search(String query, Pageable pageable);

    long countByRole(String role);

    long countByCreatedAtAfter(LocalDateTime createdAt);

    long countByLockedUntilAfter(LocalDateTime moment);
}
