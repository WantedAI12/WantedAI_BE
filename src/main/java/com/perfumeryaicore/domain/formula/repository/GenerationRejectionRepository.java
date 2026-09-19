package com.perfumeryaicore.domain.formula.repository;

import com.perfumeryaicore.domain.formula.entity.GenerationRejection;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GenerationRejectionRepository extends JpaRepository<GenerationRejection, Long> {

	List<GenerationRejection> findByRequestIdOrderByCreatedAtDesc(Long requestId);

	List<GenerationRejection> findByProjectId(Long projectId);

	/** 서비스 운영 상태: 프로젝트들의 기권(안전 조건을 만족하는 조향식을 못 찾음) 누계. */
	long countByProjectIdIn(Collection<Long> projectIds);
}
