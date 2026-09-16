package com.perfumeryaicore.domain.request.service;

import com.perfumeryaicore.domain.project.service.ProjectAccessGuard;
import com.perfumeryaicore.domain.request.dto.response.ProjectProgressResponse;
import com.perfumeryaicore.domain.request.dto.response.WorkChecklistItemResponse;
import com.perfumeryaicore.domain.request.dto.response.WorkProgressResponse;
import com.perfumeryaicore.domain.request.entity.FragranceRequest;
import com.perfumeryaicore.domain.request.entity.WorkChecklistItem;
import com.perfumeryaicore.domain.request.entity.WorkChecklistItemType;
import com.perfumeryaicore.domain.request.repository.FragranceRequestRepository;
import com.perfumeryaicore.domain.request.repository.WorkChecklistItemRepository;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 향수 작업({@link FragranceRequest}) 체크리스트와 진행률(BE-090~098).
 *
 * <p>체크리스트 6항목({@link WorkChecklistItemType})은 요청 생성 시 자동으로 만들어진다
 * ({@link FragranceRequestService#create}가 이 서비스의 {@link #initialize}를 호출).
 * 항목 완료/해제는 {@code revision} 기반 낙관적 잠금으로 동시 수정을 막는다
 * ({@link com.perfumeryaicore.domain.formula.entity.CandidateMemo}와 같은 패턴).
 *
 * <p>진행률 공식(팀 확정, BE-097): 작업 진행률 = 완료 항목 수 ÷ 전체 항목 수 × 100.
 * 프로젝트 진행률은 소속 작업들의 완료/전체 항목 수를 각각 합산한 뒤 나눈다(작업별 진행률의
 * 단순 평균이 아니다). 99.9%는 고정 상한이 아니라 미완료 상태에서 반올림으로 100%가 되는 것을
 * 막는 가드다 - 실제로 전부 완료되면 100.0을 반환한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkChecklistService {

	private final WorkChecklistItemRepository checklistItemRepository;
	private final FragranceRequestRepository requestRepository;
	private final ProjectAccessGuard accessGuard;

	/** 요청 생성 시 호출된다. 이미 초기화된 요청이면 아무 것도 하지 않는다(재시도 안전). */
	@Transactional
	public void initialize(Long requestId) {
		if (!checklistItemRepository.findByRequestIdOrderByItemTypeAsc(requestId).isEmpty()) {
			return;
		}
		for (WorkChecklistItemType type : WorkChecklistItemType.values()) {
			checklistItemRepository.save(WorkChecklistItem.create(requestId, type));
		}
	}

	public List<WorkChecklistItemResponse> list(Long requestId, Long memberId) {
		getAccessibleRequest(requestId, memberId);
		return checklistItemRepository.findByRequestIdOrderByItemTypeAsc(requestId).stream()
				.map(WorkChecklistItemResponse::from)
				.toList();
	}

	@Transactional
	public WorkChecklistItemResponse setCompleted(Long requestId, Long memberId, WorkChecklistItemType itemType,
			boolean completed, int expectedRevision) {
		getAccessibleRequest(requestId, memberId);
		WorkChecklistItem item = checklistItemRepository.findByRequestIdAndItemType(requestId, itemType)
				.orElseThrow(() -> new BusinessException(ErrorCode.WORK_CHECKLIST_ITEM_NOT_FOUND));
		item.setCompleted(completed, expectedRevision, memberId);
		log.info("[REQUEST] checklist request={} item={} completed={} by={} revision={}",
				requestId, itemType, completed, memberId, item.getRevision());
		return WorkChecklistItemResponse.from(item);
	}

	/**
	 * 담당자를 배정하거나({@code assigneeId} 있음) 해제한다({@code null}). 배정 대상은 그 요청이
	 * 속한 프로젝트의 멤버여야 한다 - 다른 프로젝트 사람에게 작업을 떠넘기는 실수를 막는다.
	 */
	@Transactional
	public WorkChecklistItemResponse assign(Long requestId, Long memberId, WorkChecklistItemType itemType,
			Long assigneeId, int expectedRevision) {
		FragranceRequest request = getAccessibleRequest(requestId, memberId);
		if (assigneeId != null && !accessGuard.isMember(request.getProjectId(), assigneeId)) {
			throw new BusinessException(ErrorCode.PROJECT_MEMBER_NOT_FOUND);
		}
		WorkChecklistItem item = checklistItemRepository.findByRequestIdAndItemType(requestId, itemType)
				.orElseThrow(() -> new BusinessException(ErrorCode.WORK_CHECKLIST_ITEM_NOT_FOUND));
		item.assignTo(assigneeId, expectedRevision, memberId);
		log.info("[REQUEST] checklist request={} item={} assignedTo={} by={} revision={}",
				requestId, itemType, assigneeId, memberId, item.getRevision());
		return WorkChecklistItemResponse.from(item);
	}

	public WorkProgressResponse workProgress(Long requestId, Long memberId) {
		getAccessibleRequest(requestId, memberId);
		List<WorkChecklistItem> items = checklistItemRepository.findByRequestIdOrderByItemTypeAsc(requestId);
		int total = items.size();
		int completed = (int) items.stream().filter(WorkChecklistItem::isCompleted).count();
		return new WorkProgressResponse(requestId, completed, total, percent(completed, total));
	}

	public ProjectProgressResponse projectProgress(Long projectId, Long memberId) {
		accessGuard.requireMember(projectId, memberId);
		List<FragranceRequest> works = requestRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
		if (works.isEmpty()) {
			return new ProjectProgressResponse(projectId, 0, 0, 0, 0.0);
		}
		List<Long> requestIds = works.stream().map(FragranceRequest::getId).toList();
		List<WorkChecklistItem> items = checklistItemRepository.findByRequestIdIn(requestIds);
		int total = items.size();
		int completed = (int) items.stream().filter(WorkChecklistItem::isCompleted).count();
		return new ProjectProgressResponse(projectId, works.size(), completed, total, percent(completed, total));
	}

	/** 요청이 속한 프로젝트의 멤버만 접근 허용({@link FragranceRequestService#getAccessibleRequest}와 동일 규칙). */
	private FragranceRequest getAccessibleRequest(Long requestId, Long memberId) {
		FragranceRequest request = requestRepository.findById(requestId)
				.orElseThrow(() -> new BusinessException(ErrorCode.REQUEST_NOT_FOUND));
		if (!accessGuard.isMember(request.getProjectId(), memberId)) {
			throw new BusinessException(ErrorCode.REQUEST_ACCESS_DENIED);
		}
		return request;
	}

	/**
	 * 완료/전체 비율을 소수 첫째 자리로 반올림하되, 아직 미완료 항목이 남아있는데 반올림 때문에
	 * 100.0이 되면 99.9로 낮춘다. 전체 항목이 0개면 0.0(체크리스트 없음).
	 */
	private double percent(int completed, int total) {
		if (total == 0) {
			return 0.0;
		}
		double raw = completed * 100.0 / total;
		double rounded = Math.round(raw * 10) / 10.0;
		if (completed < total && rounded >= 100.0) {
			return 99.9;
		}
		return rounded;
	}
}
