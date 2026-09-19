package com.perfumeryaicore.domain.request.service;

import com.perfumeryaicore.domain.request.dto.request.CreateFragranceRequestRequest;
import com.perfumeryaicore.domain.request.dto.request.UpdateFragranceRequestRequest;
import com.perfumeryaicore.domain.request.dto.response.FragranceRequestResponse;
import com.perfumeryaicore.domain.request.entity.FragranceRequest;
import com.perfumeryaicore.domain.request.entity.RequestStatus;
import com.perfumeryaicore.domain.request.repository.FragranceRequestRepository;
import com.perfumeryaicore.domain.project.service.ProjectAccessGuard;
import com.perfumeryaicore.global.common.ProjectRole;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import com.perfumeryaicore.global.response.PageResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자연어 향 요청의 생성·조회·보완·확정. 외부 AI 호출 없이 정규화와 검증만 수행한다.
 * 접근 제어는 요청이 속한 프로젝트의 멤버십 기준({@link ProjectAccessGuard}).
 *
 * <p>쓰기(생성/수정/확정)는 멤버십만으로 부족하다 — SUPPLIER·AUDITOR 같은 읽기·외부 협업 역할이
 * 요청을 만들거나 바꾸지 못하게 {@link #WRITE_ROLES}로 제한한다(BE-004).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FragranceRequestService {

	private static final ProjectRole[] WRITE_ROLES = {
			ProjectRole.PERFUMER, ProjectRole.FRAGRANCE_RND, ProjectRole.PRODUCT_BRAND
	};

	private final FragranceRequestRepository requestRepository;
	private final ProjectAccessGuard accessGuard;
	private final WorkChecklistService workChecklistService;

	@Transactional
	public FragranceRequestResponse create(Long projectId, Long memberId, CreateFragranceRequestRequest dto) {
		if (!accessGuard.isMember(projectId, memberId)) {
			throw new BusinessException(ErrorCode.REQUEST_ACCESS_DENIED);
		}
		accessGuard.requireWriteRole(projectId, memberId, WRITE_ROLES);
		return createInternal(projectId, memberId, dto);
	}

	/**
	 * BE-091~093: 프로젝트 생성 직후 온보딩 전용 진입점. 막 생성된 프로젝트는 생성자(ORG_ADMIN)
	 * 외에 멤버가 없어 {@link #WRITE_ROLES} 검사를 통과할 수 없으므로 건너뛴다 - 프로젝트를
	 * 실제로 막 만들었다는 사실 자체가 호출자(ProjectOnboardingService)에서 이미 보장된다.
	 * 일반적인(둘 이상 멤버가 있는) 프로젝트의 요청 생성에는 쓰지 않는다.
	 */
	@Transactional
	FragranceRequestResponse createAsProjectCreator(Long projectId, Long memberId, CreateFragranceRequestRequest dto) {
		return createInternal(projectId, memberId, dto);
	}

	private FragranceRequestResponse createInternal(Long projectId, Long memberId, CreateFragranceRequestRequest dto) {
		FragranceRequest request = FragranceRequest.create(projectId, memberId, dto.rawText());
		request.applyUpdate(
				null,
				dto.productCategory(),
				dto.targetRegion(),
				dto.riskTier(),
				dto.intensity(),
				dto.longevity(),
				dto.usageConcentrationPercent(),
				dto.maxIngredientCount(),
				dto.maxIngredientPricePerKg(),
				dto.accords());
		FragranceRequest saved = requestRepository.save(request);
		workChecklistService.initialize(saved.getId());
		log.info("[REQUEST] id={} project={} status={} by={}", saved.getId(), projectId, saved.getStatus(), memberId);
		return toResponse(saved);
	}

	/**
	 * 프로젝트 안에서 이 요청이 몇 번째인지(1부터). 요청 ID는 전체 프로젝트가 공용으로 쓰는 자동 증가
	 * 값이라 화면 번호로 쓰면 다른 프로젝트·다른 사용자의 요청까지 이어 붙은 누적 번호가 보인다.
	 * 요청은 프로젝트를 지울 때만 함께 삭제되고 개별 삭제가 없어, 저장 컬럼 없이 "같은 프로젝트에서
	 * 이 요청까지의 개수"로 계산해도 번호가 바뀌지 않는다.
	 */
	public int requestNumber(Long projectId, Long requestId) {
		return (int) requestRepository.countByProjectIdAndIdLessThanEqual(projectId, requestId);
	}

	private FragranceRequestResponse toResponse(FragranceRequest request) {
		return FragranceRequestResponse.from(request, requestNumber(request.getProjectId(), request.getId()));
	}

	/** 프로젝트에 요청이 계속 쌓이므로 페이지네이션한다(BE-085). */
	public PageResponse<FragranceRequestResponse> list(
			Long projectId, Long memberId, RequestStatus status, Pageable pageable) {
		if (!accessGuard.isMember(projectId, memberId)) {
			throw new BusinessException(ErrorCode.REQUEST_ACCESS_DENIED);
		}
		var page = (status == null)
				? requestRepository.findByProjectIdOrderByCreatedAtDesc(projectId, pageable)
				: requestRepository.findByProjectIdAndStatusOrderByCreatedAtDesc(projectId, status, pageable);
		// 항목마다 개수를 세면 N번 조회하게 되므로, 프로젝트의 요청 ID를 한 번에 가져와 순번을 매긴다.
		Map<Long, Integer> numberById = new HashMap<>();
		List<Long> orderedIds = requestRepository.findIdsByProjectIdOrderByIdAsc(projectId);
		for (int i = 0; i < orderedIds.size(); i++) {
			numberById.put(orderedIds.get(i), i + 1);
		}
		return PageResponse.of(page.map(r -> {
			Integer number = numberById.get(r.getId());
			return FragranceRequestResponse.from(r, number != null ? number : requestNumber(projectId, r.getId()));
		}));
	}

	public FragranceRequestResponse get(Long requestId, Long memberId) {
		return toResponse(getAccessibleRequest(requestId, memberId));
	}

	@Transactional
	public FragranceRequestResponse update(Long requestId, Long memberId, UpdateFragranceRequestRequest dto) {
		FragranceRequest request = getAccessibleRequest(requestId, memberId);
		accessGuard.requireWriteRole(request.getProjectId(), memberId, WRITE_ROLES);
		request.applyUpdate(
				dto.rawText(),
				dto.productCategory(),
				dto.targetRegion(),
				dto.riskTier(),
				dto.intensity(),
				dto.longevity(),
				dto.usageConcentrationPercent(),
				dto.maxIngredientCount(),
				dto.maxIngredientPricePerKg(),
				dto.accords());
		return toResponse(request);
	}

	@Transactional
	public FragranceRequestResponse confirm(Long requestId, Long memberId) {
		FragranceRequest request = getAccessibleRequest(requestId, memberId);
		accessGuard.requireWriteRole(request.getProjectId(), memberId, WRITE_ROLES);
		request.confirm();
		log.info("[REQUEST] id={} CONFIRMED by={}", requestId, memberId);
		return toResponse(request);
	}

	/**
	 * 후보 생성 등 다른 도메인이 확정된 요청 엔티티가 필요할 때 사용한다.
	 * (formula 도메인에서 호출 — request → formula 워크플로 방향)
	 */
	public FragranceRequest getConfirmedRequest(Long requestId, Long memberId) {
		FragranceRequest request = getAccessibleRequest(requestId, memberId);
		if (!request.isConfirmed()) {
			throw new BusinessException(ErrorCode.REQUEST_NOT_CONFIRMED);
		}
		return request;
	}

	/** 요청이 속한 프로젝트의 멤버만 접근 허용. */
	FragranceRequest getAccessibleRequest(Long requestId, Long memberId) {
		FragranceRequest request = requestRepository.findById(requestId)
				.orElseThrow(() -> new BusinessException(ErrorCode.REQUEST_NOT_FOUND));
		if (!accessGuard.isMember(request.getProjectId(), memberId)) {
			throw new BusinessException(ErrorCode.REQUEST_ACCESS_DENIED);
		}
		return request;
	}
}
