package com.perfumeryaicore.domain.formula.repository;

import com.perfumeryaicore.domain.formula.entity.CandidateVersionIngredient;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateVersionIngredientRepository extends JpaRepository<CandidateVersionIngredient, Long> {

	List<CandidateVersionIngredient> findByCandidateVersionId(Long candidateVersionId);

	List<CandidateVersionIngredient> findByCandidateVersionIdIn(List<Long> candidateVersionIds);
}
