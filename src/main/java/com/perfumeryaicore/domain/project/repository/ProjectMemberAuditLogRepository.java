package com.perfumeryaicore.domain.project.repository;

import com.perfumeryaicore.domain.project.entity.ProjectMemberAuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberAuditLogRepository extends JpaRepository<ProjectMemberAuditLog, Long> {

	List<ProjectMemberAuditLog> findByProjectIdOrderByCreatedAtDesc(Long projectId);
}
