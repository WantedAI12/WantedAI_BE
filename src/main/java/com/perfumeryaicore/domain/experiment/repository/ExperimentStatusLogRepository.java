package com.perfumeryaicore.domain.experiment.repository;

import com.perfumeryaicore.domain.experiment.entity.ExperimentStatusLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExperimentStatusLogRepository extends JpaRepository<ExperimentStatusLog, Long> {

	List<ExperimentStatusLog> findByCandidateIdOrderByCreatedAtDesc(Long candidateId);
}
