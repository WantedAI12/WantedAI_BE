package com.perfumeryaicore.domain.ingredient.repository;

import com.perfumeryaicore.domain.ingredient.entity.IngredientMaster;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientMasterRepository extends JpaRepository<IngredientMaster, Long> {

	Optional<IngredientMaster> findByExternalId(String externalId);

	boolean existsByExternalId(String externalId);

	List<IngredientMaster> findByExternalIdIn(Collection<String> externalIds);

	Page<IngredientMaster> findByNameContainingIgnoreCaseOrCasNumberContainingIgnoreCase(
			String name, String casNumber, Pageable pageable);
}
