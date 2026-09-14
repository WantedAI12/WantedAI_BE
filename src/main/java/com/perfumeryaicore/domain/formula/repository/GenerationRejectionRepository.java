package com.perfumeryaicore.domain.formula.repository;

import com.perfumeryaicore.domain.formula.entity.GenerationRejection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GenerationRejectionRepository extends JpaRepository<GenerationRejection, Long> {

	List<GenerationRejection> findByRequestIdOrderByCreatedAtDesc(Long requestId);
}
