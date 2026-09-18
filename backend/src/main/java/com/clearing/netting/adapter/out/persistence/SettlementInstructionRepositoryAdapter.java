package com.clearing.netting.adapter.out.persistence;

import com.clearing.netting.adapter.out.persistence.repo.SettlementInstructionJpaRepository;
import com.clearing.netting.domain.model.SettlementInstruction;
import com.clearing.netting.domain.port.out.SettlementInstructionRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class SettlementInstructionRepositoryAdapter implements SettlementInstructionRepositoryPort {

    private final SettlementInstructionJpaRepository repository;

    public SettlementInstructionRepositoryAdapter(SettlementInstructionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<SettlementInstruction> saveAll(List<SettlementInstruction> instructions) {
        return repository
                .saveAll(instructions.stream().map(PersistenceMapper::toEntity).collect(Collectors.toList()))
                .stream().map(PersistenceMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<SettlementInstruction> findByRunId(String runId) {
        return repository.findByRunId(runId).stream()
                .map(PersistenceMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<SettlementInstruction> findById(String instructionId) {
        return repository.findById(instructionId).map(PersistenceMapper::toDomain);
    }
}
