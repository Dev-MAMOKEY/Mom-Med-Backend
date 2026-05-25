package mamokey.mom_med.backend.parent.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 부모 단골 약국 엔티티 (Slice 08).
 *
 * <p>app.parent_pharmacies 테이블과 매핑됩니다.
 * HIRA 약국정보서비스(sno 12100) 응답을 파싱해 적재합니다.</p>
 */
@Entity
@Table(name = "parent_pharmacies", schema = "app")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParentPharmacy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id", nullable = false, updatable = false)
    private UUID parentId;

    @Column(nullable = false, length = 500)
    private String ykiho;

    @Column(name = "yadm_nm", nullable = false, length = 200)
    private String yadmNm;

    @Column(length = 500)
    private String addr;

    @Column(length = 50)
    private String telno;

    @Column(name = "x_pos", precision = 12, scale = 6)
    private BigDecimal xPos;

    @Column(name = "y_pos", precision = 12, scale = 6)
    private BigDecimal yPos;

    @Column(name = "is_regular", nullable = false)
    private boolean regular = false;

    @Column(name = "visit_count", nullable = false)
    private int visitCount = 0;

    @Column(name = "last_visited")
    private LocalDate lastVisited;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMPTZ DEFAULT NOW()")
    private LocalDateTime createdAt = LocalDateTime.now();

    // ─── 생성 ─────────────────────────────────────────────────────────────

    public static ParentPharmacy create(
            UUID parentId,
            String ykiho,
            String yadmNm,
            String addr,
            String telno,
            BigDecimal xPos,
            BigDecimal yPos,
            boolean regular
    ) {
        ParentPharmacy p = new ParentPharmacy();
        p.parentId = parentId;
        p.ykiho = ykiho;
        p.yadmNm = yadmNm;
        p.addr = addr;
        p.telno = telno;
        p.xPos = xPos;
        p.yPos = yPos;
        p.regular = regular;
        return p;
    }

    // ─── 수정 ─────────────────────────────────────────────────────────────

    public void markRegular(boolean regular) {
        this.regular = regular;
    }

    public void recordVisit() {
        this.visitCount++;
        this.lastVisited = LocalDate.now();
    }
}
