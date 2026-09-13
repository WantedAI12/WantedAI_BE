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
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

	@Transactional
	public FragranceRequestResponse create(Long projectId, Long memberId, CreateFragranceRequestRequest dto) {
		if (!accessGuard.isMember(projectId, memberId)) {
			throw new BusinessException(ErrorCode.REQUEST_ACCESS_DENIED);
		}
		accessGuard.requireRole(projectId, memberId, WRITE_ROLES);
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
		log.info("[REQUEST] id={} project={} status={} by={}", saved.getId(), projectId, saved.getStatus(), memberId);
		return FragranceRequestResponse.from(saved);
	}

	public List<FragranceRequestResponse> list(Long projectId, Long memberId, RequestStatus status) {
		if (!accessGuard.isMember(projectId, memberId)) {
			throw new BusinessException(ErrorCode.REQUEST_ACCESS_DENIED);
		}
		List<FragranceRequest> rows = (status == null)
				? requestRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
				: requestRepository.findByProjectIdAndStatusOrderByCreatedAtDesc(projectId, status);
		return rows.stream().map(FragranceRequestResponse::from).toList();
	}

	public FragranceRequestResponse get(Long requestId, Long memberId) {
		return FragranceRequestResponse.from(getAccessibleRequest(requestId, memberId));
	}

	@Transactional
	public FragranceRequestResponse update(Long requestId, Long memberId, UpdateFragranceRequestRequest dto) {
		FragranceRequest request = getAccessibleRequest(requestId, memberId);
		accessGuard.requireRole(request.getProjectId(), memberId, WRITE_ROLES);
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
		return FragranceRequestResponse.from(request);
	}

	@Transactional
	public FragranceRequestResponse confirm(Long requestId, Long memberId) {
		FragranceRequest request = getAccessibleRequest(requestId, memberId);
		accessGuard.requireRole(request.getProjectId(), memberId, WRITE_ROLES);
		request.confirm();
		log.info("[REQUEST] id={} CONFIRMED by={}", requestId, memberId);
		return FragranceRequestResponse.from(request);
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
