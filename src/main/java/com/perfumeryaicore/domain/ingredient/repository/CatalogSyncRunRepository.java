package com.perfumeryaicore.domain.ingredient.repository;

import com.perfumeryaicore.domain.ingredient.entity.CatalogSyncRun;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogSyncRunRepository extends JpaRepository<CatalogSyncRun, Long> {

	Optional<CatalogSyncRun> findByJobId(Long jobId);

	Optional<CatalogSyncRun> findFirstByOrderBySyncedAtDesc();

	List<CatalogSyncRun> findByProjectId(Long projectId);
}
