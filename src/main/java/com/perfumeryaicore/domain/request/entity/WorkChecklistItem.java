package com.perfumeryaicore.domain.request.entity;

import com.perfumeryaicore.global.common.BaseTimeEntity;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 향수 작업 체크리스트 항목 하나. {@link FragranceRequest} 생성 시 {@link WorkChecklistItemType}
 * 6개가 전부 미완료 상태로 자동 생성된다.
 *
 * <p>{@code revision}으로 낙관적 잠금을 구현한다({@link com.perfumeryaicore.domain.formula.entity.CandidateMemo}와
 * 같은 패턴) — 동시에 같은 항목을 체크/해제하면 나중 요청이 {@link ErrorCode#WORK_CHECKLIST_ITEM_CONFLICT}로 거부된다.
 */
@Entity
@Getter
@Table(
		name = "work_checklist_items",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_work_checklist_items_request_type", columnNames = {"request_id", "item_type"}),
		indexes = @Index(name = "idx_work_checklist_items_request_id", columnList = "request_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkChecklistItem extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "request_id", nullable = false)
	private Long requestId;

	@Enumerated(EnumType.STRING)
	@Column(name = "item_type", nullable = false, length = 30)
	private WorkChecklistItemType itemType;

	@Column(nullable = false)
	private boolean completed;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	@Column(name = "completed_by")
	private Long completedBy;

	@Column(nullable = false)
	private int revision;

	private WorkChecklistItem(Long requestId, WorkChecklistItemType itemType) {
		this.requestId = requestId;
		this.itemType = itemType;
		this.completed = false;
		this.revision = 0;
	}

	public static WorkChecklistItem create(Long requestId, WorkChecklistItemType itemType) {
		return new WorkChecklistItem(requestId, itemType);
	}

	/**
	 * @param expectedRevision 클라이언트가 마지막으로 읽은 revision. 현재 값과 다르면 그 사이 다른
	 *     사용자가 먼저 바꾼 것이므로 {@link ErrorCode#WORK_CHECKLIST_ITEM_CONFLICT}로 거부한다.
	 */
	public void setCompleted(boolean completed, int expectedRevision, Long editorId) {
		if (this.revision != expectedRevision) {
			throw new BusinessException(ErrorCode.WORK_CHECKLIST_ITEM_CONFLICT);
		}
		this.completed = completed;
		this.completedAt = completed ? LocalDateTime.now() : null;
		this.completedBy = completed ? editorId : null;
		this.revision++;
	}
}
