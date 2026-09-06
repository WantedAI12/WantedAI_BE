package com.perfumeryaicore.domain.supply.repository;

import com.perfumeryaicore.domain.supply.entity.SupplyChange;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplyChangeRepository extends JpaRepository<SupplyChange, Long> {
}
