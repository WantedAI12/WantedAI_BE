package com.perfumeryaicore.domain.safety.repository;

import com.perfumeryaicore.domain.safety.entity.ApprovalGate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalGateRepository extends JpaRepository<ApprovalGate, Long> {

	List<ApprovalGate> findByCandidateIdOrderByCreatedAtDesc(Long candidateId);

	Optional<ApprovalGate> findFirstByCandidateIdOrderByCreatedAtDesc(Long candidateId);
}
