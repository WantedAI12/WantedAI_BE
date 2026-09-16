package com.perfumeryaicore.domain.ingredient.repository;

import com.perfumeryaicore.domain.ingredient.entity.IngredientImportFailure;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientImportFailureRepository extends JpaRepository<IngredientImportFailure, Long> {

	Page<IngredientImportFailure> findByResolvedAtIsNullOrderByCreatedAtAsc(Pageable pageable);
}
