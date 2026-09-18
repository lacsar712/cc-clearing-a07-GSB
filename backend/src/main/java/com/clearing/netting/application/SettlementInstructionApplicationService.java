package com.clearing.netting.application;

import com.clearing.netting.domain.exception.DomainException;
import com.clearing.netting.domain.model.NetPosition;
import com.clearing.netting.domain.model.NettingRun;
import com.clearing.netting.domain.model.NettingRunStatus;
import com.clearing.netting.domain.model.SettlementInstruction;
import com.clearing.netting.domain.port.out.NetPositionRepositoryPort;
import com.clearing.netting.domain.port.out.NettingRunRepositoryPort;
import com.clearing.netting.domain.port.out.SettlementInstructionRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class SettlementInstructionApplicationService {

    private final NettingRunRepositoryPort runRepository;
    private final NetPositionRepositoryPort positionRepository;
    private final SettlementInstructionRepositoryPort instructionRepository;

    public SettlementInstructionApplicationService(
            NettingRunRepositoryPort runRepository,
            NetPositionRepositoryPort positionRepository,
            SettlementInstructionRepositoryPort instructionRepository) {
        this.runRepository = runRepository;
        this.positionRepository = positionRepository;
        this.instructionRepository = instructionRepository;
    }

    @Transactional(readOnly = true)
    public List<SettlementInstruction> listByRun(String runId) {
        return instructionRepository.findByRunId(runId);
    }

    /**
     * Generate the settlement instruction preview for a completed run.
     * Idempotent: if instructions already exist (previewed or released) they are returned unchanged.
     */
    @Transactional
    public List<SettlementInstruction> preview(String runId) {
        getCompletedRun(runId);

        List<SettlementInstruction> existing = instructionRepository.findByRunId(runId);
        if (!existing.isEmpty()) {
            return existing;
        }

        List<NetPosition> positions = positionRepository.findByRunId(runId);
        List<SettlementInstruction> generated = new ArrayList<>();
        for (NetPosition p : positions) {
            if (p.getNetAmount().signum() == 0) {
                continue;
            }
            generated.add(SettlementInstruction.preview(p.getRunId(), p.getMemberId(), p.getCurrency(), p.getNetAmount()));
        }
        if (generated.isEmpty()) {
            throw new DomainException("NO_NET_POSITION", "no non-zero net positions to preview for run: " + runId);
        }
        return instructionRepository.saveAll(generated);
    }

    /**
     * Release the previewed instructions. Operator only — authorization is enforced at the web adapter.
     */
    @Transactional
    public List<SettlementInstruction> release(String runId, String operator) {
        getCompletedRun(runId);
        List<SettlementInstruction> instructions = instructionRepository.findByRunId(runId);
        if (instructions.isEmpty()) {
            throw new DomainException("NOT_PREVIEWED", "generate the preview before releasing: " + runId);
        }
        for (SettlementInstruction instruction : instructions) {
            instruction.release(operator);
        }
        return instructionRepository.saveAll(instructions);
    }

    private NettingRun getCompletedRun(String runId) {
        NettingRun run = runRepository.findById(runId)
                .orElseThrow(() -> new DomainException("RUN_NOT_FOUND", "netting run not found: " + runId));
        if (run.getStatus() != NettingRunStatus.COMPLETED) {
            throw new DomainException("INVALID_STATE",
                    "only COMPLETED runs can generate settlement instructions, current status: " + run.getStatus());
        }
        return run;
    }
}
