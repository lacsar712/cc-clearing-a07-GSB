package com.clearing.netting.domain.port.out;

import com.clearing.netting.domain.model.SettlementInstruction;

import java.util.List;
import java.util.Optional;

public interface SettlementInstructionRepositoryPort {
    List<SettlementInstruction> saveAll(List<SettlementInstruction> instructions);

    List<SettlementInstruction> findByRunId(String runId);

    Optional<SettlementInstruction> findById(String instructionId);
}
