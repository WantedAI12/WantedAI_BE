package com.perfumeryaicore.domain.project.repository;

import com.perfumeryaicore.domain.project.entity.ProjectMemberAuditLog;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberAuditLogRepository extends JpaRepository<ProjectMemberAuditLog, Long> {

	List<ProjectMemberAuditLog> findByProjectIdOrderByCreatedAtDesc(Long projectId);

	Page<ProjectMemberAuditLog> findByProjectIdOrderByCreatedAtDesc(Long projectId, Pageable pageable);

	/** 서비스 운영 상태: 프로젝트들의 최근 멤버 변경 이력. */
	List<ProjectMemberAuditLog> findByProjectIdInOrderByCreatedAtDesc(Collection<Long> projectIds, Pageable pageable);
}
