package org.group7.wearwise.entity;

import jakarta.persistence.*;
import lombok.*;
import org.group7.wearwise.enums.ClothingCategory;
import org.group7.wearwise.enums.ClothingCondition;
import org.group7.wearwise.enums.ClothingStatus;
import org.group7.wearwise.enums.Season;
import org.group7.wearwise.enums.Style;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

@Entity
@Table(name = "clothing_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClothingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String color;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClothingCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Season season;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Style style;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_condition", nullable = false)
    @ColumnDefault("'GOOD'")
    @Builder.Default
    private ClothingCondition condition = ClothingCondition.GOOD;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @ColumnDefault("'AVAILABLE'")
    @Builder.Default
    private ClothingStatus status = ClothingStatus.AVAILABLE;

    @Column(nullable = false)
    @ColumnDefault("0")
    @Builder.Default
    private Integer wearCount = 0;

    private LocalDateTime lastWornAt;

    private Boolean favorite;

    private String imageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private AppUser owner;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();

        if (this.favorite == null) {
            this.favorite = false;
        }

        if (this.condition == null) {
            this.condition = ClothingCondition.GOOD;
        }

        if (this.status == null) {
            this.status = ClothingStatus.AVAILABLE;
        }

        if (this.wearCount == null) {
            this.wearCount = 0;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
