package com.perfumeryaicore.domain.formula.service;

import com.perfumeryaicore.domain.formula.dto.response.CandidateResponse;
import com.perfumeryaicore.domain.formula.dto.response.CandidateVersionResponse;
import com.perfumeryaicore.domain.formula.entity.Candidate;
import com.perfumeryaicore.domain.formula.entity.CandidateVersion;
import com.perfumeryaicore.domain.formula.repository.CandidateRepository;
import com.perfumeryaicore.domain.formula.repository.CandidateVersionIngredientRepository;
import com.perfumeryaicore.domain.formula.repository.CandidateVersionRepository;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 후보·후보 버전 조회. 접근 제어는 {@code Candidate.createdBy} 기준
 * (TODO(project): 프로젝트 멤버 접근으로 확장).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CandidateService {

	private final CandidateRepository candidateRepository;
	private final CandidateVersionRepository candidateVersionRepository;
	private final CandidateVersionIngredientRepository ingredientRepository;
	private final CandidateVersionMapper versionMapper;

	public List<CandidateResponse> listByRequest(Long requestId, Long memberId) {
		return candidateRepository.findByRequestIdOrderByCreatedAtDesc(requestId).stream()
				.filter(c -> c.isOwnedBy(memberId))
				.map(this::toResponse)
				.toList();
	}

	public CandidateResponse get(Long candidateId, Long memberId) {
		return toResponse(getAccessibleCandidate(candidateId, memberId));
	}

	public List<CandidateVersionResponse> versions(Long candidateId, Long memberId) {
		Candidate candidate = getAccessibleCandidate(candidateId, memberId);
		return candidateVersionRepository.findByCandidateIdOrderByCreatedAtDesc(candidate.getId()).stream()
				.map(this::toVersionResponse)
				.toList();
	}

	/**
	 * 후보의 현재 버전 원문을 반환한다. safety/prediction 도메인이 각자 필요한 구간만 파싱해 쓴다
	 * (formula → safety/prediction 워크플로 방향, {@link CandidateVersionRawView} 참고).
	 */
	public CandidateVersionRawView getCurrentVersionRaw(Long candidateId, Long memberId) {
		Candidate candidate = getAccessibleCandidate(candidateId, memberId);
		if (candidate.getCurrentVersionId() == null) {
			throw new BusinessException(ErrorCode.CANDIDATE_VERSION_NOT_FOUND);
		}
		CandidateVersion version = candidateVersionRepository.findById(candidate.getCurrentVersionId())
				.orElseThrow(() -> new BusinessException(ErrorCode.CANDIDATE_VERSION_NOT_FOUND));
		return new CandidateVersionRawView(candidate.getId(), version.getId(), version.getRawResponse());
	}

	/** 접근 가능한 후보인지만 확인한다(존재 + 소유자). 다른 도메인의 접근 제어 재사용용. */
	public void assertAccessible(Long candidateId, Long memberId) {
		getAccessibleCandidate(candidateId, memberId);
	}

	public CandidateVersionResponse version(Long versionId, Long memberId) {
		CandidateVersion version = candidateVersionRepository.findById(versionId)
				.orElseThrow(() -> new BusinessException(ErrorCode.CANDIDATE_VERSION_NOT_FOUND));
		Candidate candidate = candidateRepository.findById(version.getCandidateId())
				.orElseThrow(() -> new BusinessException(ErrorCode.CANDIDATE_NOT_FOUND));
		if (!candidate.isOwnedBy(memberId)) {
			throw new BusinessException(ErrorCode.CANDIDATE_ACCESS_DENIED);
		}
		return toVersionResponse(version);
	}

	private Candidate getAccessibleCandidate(Long candidateId, Long memberId) {
		Candidate candidate = candidateRepository.findById(candidateId)
				.orElseThrow(() -> new BusinessException(ErrorCode.CANDIDATE_NOT_FOUND));
		if (!candidate.isOwnedBy(memberId)) {
			throw new BusinessException(ErrorCode.CANDIDATE_ACCESS_DENIED);
		}
		return candidate;
	}

	private CandidateResponse toResponse(Candidate candidate) {
		CandidateVersionResponse current = null;
		if (candidate.getCurrentVersionId() != null) {
			CandidateVersion version = candidateVersionRepository.findById(candidate.getCurrentVersionId())
					.orElse(null);
			current = version == null ? null : toVersionResponse(version);
		}
		return new CandidateResponse(candidate.getId(), candidate.getRequestId(), candidate.getStatus(), current);
	}

	private CandidateVersionResponse toVersionResponse(CandidateVersion version) {
		return versionMapper.toResponse(version, ingredientRepository.findByCandidateVersionId(version.getId()));
	}
}
