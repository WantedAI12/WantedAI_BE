package com.perfumeryaicore.domain.formula.repository;

import com.perfumeryaicore.domain.formula.entity.CandidateMemo;
import com.perfumeryaicore.domain.formula.entity.CandidateMemoType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateMemoRepository extends JpaRepository<CandidateMemo, Long> {

	List<CandidateMemo> findByCandidateId(Long candidateId);

	Optional<CandidateMemo> findByCandidateIdAndMemoType(Long candidateId, CandidateMemoType memoType);
}
