package com.warehouse.warehouse_platform.tenant.counting;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "count_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CountSession {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CountStatus status = CountStatus.OPEN;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "posted_by", length = 255)
    private String postedBy;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "voided_by", length = 255)
    private String voidedBy;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "count_session_locations", joinColumns = @JoinColumn(name = "session_id"))
    @Column(name = "location_id", nullable = false)
    @Builder.Default
    private Set<UUID> locationIds = new LinkedHashSet<>();

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (status == null) {
            status = CountStatus.OPEN;
        }
        if (locationIds == null) {
            locationIds = new LinkedHashSet<>();
        }
    }

    @PreUpdate
    public void preUpdate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (locationIds == null) {
            locationIds = new LinkedHashSet<>();
        }
    }
}