package com.perfumeryaicore.domain.ingredient.repository;

import com.perfumeryaicore.domain.ingredient.entity.IngredientMaster;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientMasterRepository extends JpaRepository<IngredientMaster, Long> {

	Optional<IngredientMaster> findByExternalId(String externalId);

	boolean existsByExternalId(String externalId);

	List<IngredientMaster> findByNameContainingIgnoreCaseOrCasNumberContainingIgnoreCase(
			String name, String casNumber);
}
