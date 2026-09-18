package com.clearing.netting.adapter.out.persistence.repo;

import com.clearing.netting.adapter.out.persistence.entity.SettlementInstructionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SettlementInstructionJpaRepository
        extends JpaRepository<SettlementInstructionJpaEntity, String> {

    List<SettlementInstructionJpaEntity> findByRunIdOrderByMemberIdAsc(String runId);

    Optional<SettlementInstructionJpaEntity> findByRunIdAndMemberId(String runId, String memberId);
}
