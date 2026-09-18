package com.clearing.netting.adapter.out.persistence.repo;

import com.clearing.netting.adapter.out.persistence.entity.SettlementInstructionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettlementInstructionJpaRepository
        extends JpaRepository<SettlementInstructionJpaEntity, String> {
    List<SettlementInstructionJpaEntity> findByRunId(String runId);
}
