package com.clearing.netting.application;

import com.clearing.netting.domain.exception.DomainException;
import com.clearing.netting.domain.model.Member;
import com.clearing.netting.domain.model.NetPosition;
import com.clearing.netting.domain.model.NettingRun;
import com.clearing.netting.domain.model.NettingRunStatus;
import com.clearing.netting.domain.model.SettlementInstruction;
import com.clearing.netting.domain.port.out.MemberRepositoryPort;
import com.clearing.netting.domain.port.out.NetPositionRepositoryPort;
import com.clearing.netting.domain.port.out.NettingRunRepositoryPort;
import com.clearing.netting.domain.port.out.SettlementInstructionRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SettlementInstructionApplicationService {

    private final NettingRunRepositoryPort runRepository;
    private final NetPositionRepositoryPort positionRepository;
    private final SettlementInstructionRepositoryPort instructionRepository;
    private final MemberRepositoryPort memberRepository;

    public SettlementInstructionApplicationService(
            NettingRunRepositoryPort runRepository,
            NetPositionRepositoryPort positionRepository,
            SettlementInstructionRepositoryPort instructionRepository,
            MemberRepositoryPort memberRepository) {
        this.runRepository = runRepository;
        this.positionRepository = positionRepository;
        this.instructionRepository = instructionRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public InstructionSet listByRun(String runId) {
        NettingRun run = requireRun(runId);
        return assemble(run, instructionRepository.findByRunIdOrderByMemberId(runId));
    }

    /**
     * Generate (or return the existing) settlement instruction preview for a completed run.
     * Previewing is idempotent: regenerating returns the same instructions and keeps the
     * original preview timestamps.
     */
    @Transactional
    public InstructionSet preview(String runId) {
        NettingRun run = requireCompletedRun(runId);

        List<SettlementInstruction> existing = instructionRepository.findByRunIdOrderByMemberId(runId);
        if (!existing.isEmpty()) {
            return assemble(run, existing);
        }

        List<NetPosition> positions = positionRepository.findByRunId(run.getRunId());
        List<SettlementInstruction> created = positions.stream()
                .filter(p -> p.getNetAmount().signum() != 0)
                .map(p -> SettlementInstruction.preview(run.getRunId(), p))
                .toList();
        if (created.isEmpty()) {
            throw new DomainException("NO_POSITIONS", "no non-zero net positions to preview for run: " + runId);
        }
        return assemble(run, instructionRepository.saveAll(created));
    }

    /**
     * Release every previewed instruction of the run. Only a previously previewed run can be
     * released, and already-released instructions are left untouched.
     */
    @Transactional
    public InstructionSet release(String runId, String operator) {
        requireCompletedRun(runId);
        if (operator == null || operator.isBlank()) {
            throw new DomainException("UNAUTHORIZED", "operator identity required to release");
        }

        List<SettlementInstruction> instructions = instructionRepository.findByRunIdOrderByMemberId(runId);
        if (instructions.isEmpty()) {
            throw new DomainException("NO_PREVIEW", "generate a preview before releasing run: " + runId);
        }

        boolean changed = false;
        for (SettlementInstruction instruction : instructions) {
            if (!instruction.isReleased()) {
                instruction.release(operator);
                changed = true;
            }
        }
        if (changed) {
            instructions = instructionRepository.saveAll(instructions);
        }
        return assemble(requireRun(runId), instructions);
    }

    private InstructionSet assemble(NettingRun run, List<SettlementInstruction> instructions) {
        Set<String> memberIds = new HashSet<>();
        for (SettlementInstruction i : instructions) {
            memberIds.add(i.getMemberId());
        }
        Map<String, String> names = new HashMap<>();
        for (Member m : memberRepository.findByIds(memberIds)) {
            names.put(m.getMemberId(), m.getName());
        }
        return new InstructionSet(run, instructions, names);
    }

    private NettingRun requireRun(String runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new DomainException("RUN_NOT_FOUND", "netting run not found: " + runId));
    }

    private NettingRun requireCompletedRun(String runId) {
        NettingRun run = requireRun(runId);
        if (run.getStatus() != NettingRunStatus.COMPLETED) {
            throw new DomainException(
                    "INVALID_STATE",
                    "only COMPLETED runs can generate settlement instructions, current status: " + run.getStatus());
        }
        return run;
    }

    public record InstructionSet(NettingRun run, List<SettlementInstruction> instructions,
                                 Map<String, String> memberNames) {
    }
}
