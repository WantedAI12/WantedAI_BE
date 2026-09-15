package com.perfumeryaicore.domain.ingredient.repository;

import com.perfumeryaicore.domain.ingredient.entity.IngredientMaster;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientMasterRepository extends JpaRepository<IngredientMaster, Long> {

	Optional<IngredientMaster> findByExternalId(String externalId);

	boolean existsByExternalId(String externalId);

	Page<IngredientMaster> findByNameContainingIgnoreCaseOrCasNumberContainingIgnoreCase(
			String name, String casNumber, Pageable pageable);
}
