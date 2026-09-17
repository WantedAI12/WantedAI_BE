package com.perfumeryaicore.domain.project.repository;

import com.perfumeryaicore.domain.project.entity.ProjectImageAsset;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectImageAssetRepository extends JpaRepository<ProjectImageAsset, Long> {

	List<ProjectImageAsset> findByProjectId(Long projectId);
}
