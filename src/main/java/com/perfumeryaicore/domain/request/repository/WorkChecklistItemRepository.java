package com.perfumeryaicore.domain.request.repository;

import com.perfumeryaicore.domain.request.entity.WorkChecklistItem;
import com.perfumeryaicore.domain.request.entity.WorkChecklistItemType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkChecklistItemRepository extends JpaRepository<WorkChecklistItem, Long> {

	List<WorkChecklistItem> findByRequestIdOrderByItemTypeAsc(Long requestId);

	Optional<WorkChecklistItem> findByRequestIdAndItemType(Long requestId, WorkChecklistItemType itemType);

	List<WorkChecklistItem> findByRequestIdIn(List<Long> requestIds);

	long countByRequestIdAndCompletedTrue(Long requestId);
}
