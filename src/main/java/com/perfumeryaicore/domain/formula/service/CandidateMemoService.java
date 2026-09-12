package com.perfumeryaicore.domain.formula.service;

import com.perfumeryaicore.domain.formula.dto.request.UpsertCandidateMemoRequest;
import com.perfumeryaicore.domain.formula.dto.response.CandidateMemoResponse;
import com.perfumeryaicore.domain.formula.entity.CandidateMemo;
import com.perfumeryaicore.domain.formula.entity.CandidateMemoType;
import com.perfumeryaicore.domain.formula.repository.CandidateMemoRepository;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 후보 메모(입력내용/검토사항/다음실험) 조회·저장(COR-B02). 원본 브리프나 다른 후보는 건드리지 않고,
 * 이 후보에 딸린 부가 텍스트만 다룬다. 접근 제어는 {@link CandidateService}에 위임한다.
 *
 * <p>동시 수정은 {@code revision} 기반 낙관적 잠금으로 막는다 — {@link ErrorCode#CANDIDATE_MEMO_CONFLICT}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CandidateMemoService {

	private final CandidateMemoRepository memoRepository;
	private final CandidateService candidateService;

	/** 메모 3종을 항상 고정된 순서로 반환한다. 저장된 적 없는 타입은 {@code revision 0}짜리 빈 응답. */
	public List<CandidateMemoResponse> list(Long candidateId, Long memberId) {
		candidateService.assertAccessible(candidateId, memberId);
		Map<CandidateMemoType, CandidateMemo> byType = memoRepository.findByCandidateId(candidateId).stream()
				.collect(Collectors.toMap(CandidateMemo::getMemoType, Function.identity()));
		return Arrays.stream(CandidateMemoType.values())
				.map(type -> byType.containsKey(type)
						? CandidateMemoResponse.from(byType.get(type))
						: CandidateMemoResponse.empty(type))
				.toList();
	}

	/**
	 * 메모를 저장한다(생성·수정 겸용). {@code dto.expectedRevision()}이 현재 상태와 다르면
	 * {@link ErrorCode#CANDIDATE_MEMO_CONFLICT}로 거부한다 — 처음 저장하는 메모는 0이어야 한다.
	 */
	@Transactional
	public CandidateMemoResponse upsert(Long candidateId, Long memberId, CandidateMemoType memoType,
			UpsertCandidateMemoRequest dto) {
		Long currentVersionId = candidateService.getCurrentVersionId(candidateId, memberId);
		Optional<CandidateMemo> existing = memoRepository.findByCandidateIdAndMemoType(candidateId, memoType);

		if (existing.isEmpty()) {
			if (dto.expectedRevision() != 0) {
				throw new BusinessException(ErrorCode.CANDIDATE_MEMO_CONFLICT,
						"메모가 이미 다른 사용자에 의해 생성되었습니다. 최신 내용을 다시 불러오세요.");
			}
			CandidateMemo memo = memoRepository.save(
					CandidateMemo.create(candidateId, memoType, dto.content(), currentVersionId, memberId));
			log.info("[FORMULA] candidate={} memo={} created by={}", candidateId, memoType, memberId);
			return CandidateMemoResponse.from(memo);
		}

		CandidateMemo memo = existing.get();
		memo.update(dto.content(), dto.expectedRevision(), currentVersionId, memberId);
		log.info("[FORMULA] candidate={} memo={} updated by={} revision={}",
				candidateId, memoType, memberId, memo.getRevision());
		return CandidateMemoResponse.from(memo);
	}
}
