package mamokey.mom_med.backend.domain.weather.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ref.region_grid 테이블과 매핑되는 기상청 격자 좌표 엔티티입니다.
 *
 * <p>부모 주소의 시/군/구/동을 기상청 nx, ny 좌표로 바꾸는 기준 데이터입니다.
 * Slice 07은 이 nx, ny를 사용해 단기예보를 조회합니다.</p>
 */
@Entity
@Table(name = "region_grid", schema = "ref")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegionGrid {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "admin_code", length = 20)
	private String adminCode;

	@Column(nullable = false, length = 50)
	private String sido;

	@Column(length = 50)
	private String sigungu;

	@Column(name = "eup_myeon_dong", length = 50)
	private String eupMyeonDong;

	@Column(nullable = false)
	private Short nx;

	@Column(nullable = false)
	private Short ny;

	@Column(precision = 10, scale = 6)
	private BigDecimal longitude;

	@Column(precision = 10, scale = 6)
	private BigDecimal latitude;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime updatedAt;

	public static RegionGrid create(
			String adminCode,
			String sido,
			String sigungu,
			String eupMyeonDong,
			Short nx,
			Short ny,
			BigDecimal longitude,
			BigDecimal latitude
	) {
		RegionGrid grid = new RegionGrid();
		grid.adminCode = adminCode;
		grid.sido = sido;
		grid.sigungu = sigungu;
		grid.eupMyeonDong = eupMyeonDong;
		grid.nx = nx;
		grid.ny = ny;
		grid.longitude = longitude;
		grid.latitude = latitude;
		return grid;
	}

	public void refresh(Short nx, Short ny, BigDecimal longitude, BigDecimal latitude, String adminCode) {
		this.nx = nx;
		this.ny = ny;
		this.longitude = longitude;
		this.latitude = latitude;
		this.adminCode = adminCode;
	}
}
