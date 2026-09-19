package com.perfumeryaicore.domain.request.repository;

import com.perfumeryaicore.domain.request.entity.FragranceRequest;
import com.perfumeryaicore.domain.request.entity.RequestStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FragranceRequestRepository extends JpaRepository<FragranceRequest, Long> {

	/** 같은 프로젝트 안에서 이 요청까지(포함)의 개수 - 프로젝트 내 표시용 순번이 된다. */
	long countByProjectIdAndIdLessThanEqual(Long projectId, Long id);

	/** 프로젝트의 요청 ID를 생성 순서대로. 목록 응답의 순번을 한 번의 조회로 계산하는 데 쓴다. */
	@Query("select r.id from FragranceRequest r where r.projectId = :projectId order by r.id asc")
	List<Long> findIdsByProjectIdOrderByIdAsc(@Param("projectId") Long projectId);

	List<FragranceRequest> findByProjectIdOrderByCreatedAtDesc(Long projectId);

	List<FragranceRequest> findByProjectIdAndStatusOrderByCreatedAtDesc(Long projectId, RequestStatus status);

	List<FragranceRequest> findByProjectIdIn(List<Long> projectIds);

	Page<FragranceRequest> findByProjectIdOrderByCreatedAtDesc(Long projectId, Pageable pageable);

	Page<FragranceRequest> findByProjectIdAndStatusOrderByCreatedAtDesc(
			Long projectId, RequestStatus status, Pageable pageable);
}
