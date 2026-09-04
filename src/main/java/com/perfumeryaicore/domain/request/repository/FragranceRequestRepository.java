package com.perfumeryaicore.domain.request.repository;

import com.perfumeryaicore.domain.request.entity.FragranceRequest;
import com.perfumeryaicore.domain.request.entity.RequestStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FragranceRequestRepository extends JpaRepository<FragranceRequest, Long> {

	List<FragranceRequest> findByProjectIdOrderByCreatedAtDesc(Long projectId);

	List<FragranceRequest> findByProjectIdAndStatusOrderByCreatedAtDesc(Long projectId, RequestStatus status);
}
