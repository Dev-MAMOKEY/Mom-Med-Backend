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
 * 부모 단골 병원 엔티티 (Slice 08).
 *
 * <p>app.parent_hospitals 테이블과 매핑됩니다.
 * HIRA 병원정보서비스(sno 11999) 응답을 파싱해 적재합니다.
 * ykiho(요양기관기호)는 HIRA 전체 API에서 병원을 식별하는 키입니다.</p>
 */
@Entity
@Table(name = "parent_hospitals", schema = "app")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParentHospital {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id", nullable = false, updatable = false)
    private UUID parentId;

    /** 요양기관기호 — HIRA API 전체에서 병원 식별 키 */
    @Column(nullable = false, length = 500)
    private String ykiho;

    /** 병원명 */
    @Column(name = "yadm_nm", nullable = false, length = 200)
    private String yadmNm;

    /** 병원 종별 (상급종합, 종합병원, 의원 등) */
    @Column(name = "cl_cd_nm", length = 50)
    private String clCdNm;

    @Column(length = 500)
    private String addr;

    @Column(length = 50)
    private String telno;

    @Column(name = "x_pos", precision = 12, scale = 6)
    private BigDecimal xPos;

    @Column(name = "y_pos", precision = 12, scale = 6)
    private BigDecimal yPos;

    /** 단골 병원 여부 */
    @Column(name = "is_regular", nullable = false)
    private boolean regular = false;

    @Column(name = "last_visited")
    private LocalDate lastVisited;

    /** 등록자 (child | self | etc.) */
    @Column(name = "added_by", nullable = false, length = 20)
    private String addedBy;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMPTZ DEFAULT NOW()")
    private LocalDateTime createdAt = LocalDateTime.now();

    // ─── 생성 ─────────────────────────────────────────────────────────────

    public static ParentHospital create(
            UUID parentId,
            String ykiho,
            String yadmNm,
            String clCdNm,
            String addr,
            String telno,
            BigDecimal xPos,
            BigDecimal yPos,
            boolean regular,
            String addedBy
    ) {
        ParentHospital h = new ParentHospital();
        h.parentId = parentId;
        h.ykiho = ykiho;
        h.yadmNm = yadmNm;
        h.clCdNm = clCdNm;
        h.addr = addr;
        h.telno = telno;
        h.xPos = xPos;
        h.yPos = yPos;
        h.regular = regular;
        h.addedBy = addedBy;
        return h;
    }

    // ─── 수정 ─────────────────────────────────────────────────────────────

    public void markRegular(boolean regular) {
        this.regular = regular;
    }

    public void recordVisit(LocalDate visitDate) {
        this.lastVisited = visitDate;
    }
}
