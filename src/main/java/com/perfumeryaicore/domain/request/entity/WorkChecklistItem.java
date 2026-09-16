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
 * 같은 패턴) — 완료 상태·담당자 배정 어느 쪽이든 동시에 바꾸면 나중 요청이
 * {@link ErrorCode#WORK_CHECKLIST_ITEM_CONFLICT}로 거부된다(두 변경이 하나의 revision을 공유).
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

	/** 이 항목을 처리할 담당자. 미배정이면 {@code null}(프로젝트 멤버 누구나 처리 가능한 상태). */
	@Column(name = "assigned_to")
	private Long assignedTo;

	@Column(name = "assigned_by")
	private Long assignedBy;

	@Column(name = "assigned_at")
	private LocalDateTime assignedAt;

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

	/**
	 * 담당자를 지정하거나({@code assigneeId} 있음) 배정을 해제한다({@code null}). 완료 여부와
	 * 독립적으로 바꿀 수 있다 - 이미 완료된 항목의 담당 기록을 정정하는 경우도 있어서다.
	 *
	 * @param expectedRevision {@link #setCompleted}와 같은 낙관적 잠금 - 완료/배정 어느 쪽이든
	 *     먼저 바뀌면 나머지 요청은 409로 거부된다(하나의 revision을 공유).
	 */
	public void assignTo(Long assigneeId, int expectedRevision, Long assignerId) {
		if (this.revision != expectedRevision) {
			throw new BusinessException(ErrorCode.WORK_CHECKLIST_ITEM_CONFLICT);
		}
		this.assignedTo = assigneeId;
		this.assignedBy = assigneeId != null ? assignerId : null;
		this.assignedAt = assigneeId != null ? LocalDateTime.now() : null;
		this.revision++;
	}
}
