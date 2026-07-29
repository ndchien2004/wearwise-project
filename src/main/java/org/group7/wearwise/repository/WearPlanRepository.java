package org.group7.wearwise.repository;

import org.group7.wearwise.entity.WearPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WearPlanRepository extends JpaRepository<WearPlan, Long> {

    List<WearPlan> findAllByOwner_UsernameOrderByStartDateDesc(String ownerUsername);

    Optional<WearPlan> findByIdAndOwner_Username(Long id, String ownerUsername);

    /**
     * Đợt còn hiệu lực tại một ngày — dùng cho thẻ kế hoạch ở trang chủ.
     *
     * <p>Về lý thuyết có thể có nhiều đợt chồng nhau (người dùng lên hai kế hoạch cùng tuần), nên
     * trả về danh sách và để tầng service chọn, thay vì {@code Optional} rồi vỡ khi trùng.</p>
     */
    List<WearPlan> findAllByOwner_UsernameAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
            String ownerUsername,
            LocalDate onOrBefore,
            LocalDate onOrAfter
    );
}
