package com.perfumeryaicore.domain.project.repository;

import com.perfumeryaicore.domain.project.entity.Project;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {

	List<Project> findByIdInOrderByCreatedAtDesc(List<Long> ids);
}
