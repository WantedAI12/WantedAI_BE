package com.perfumeryaicore.domain.supply.repository;

import com.perfumeryaicore.domain.supply.entity.SupplyChange;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplyChangeRepository extends JpaRepository<SupplyChange, Long> {

	List<SupplyChange> findByProjectId(Long projectId);

	Optional<SupplyChange> findByChangeSourceId(String changeSourceId);
}
