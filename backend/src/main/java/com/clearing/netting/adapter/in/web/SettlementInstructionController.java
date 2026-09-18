package com.clearing.netting.adapter.in.web;

import com.clearing.netting.adapter.in.web.auth.AuthContext;
import com.clearing.netting.adapter.in.web.auth.AuthUser;
import com.clearing.netting.application.SettlementInstructionApplicationService;
import com.clearing.netting.application.SettlementInstructionApplicationService.InstructionSet;
import com.clearing.netting.domain.model.NettingRun;
import com.clearing.netting.domain.model.NettingRunStatus;
import com.clearing.netting.domain.model.SettlementDirection;
import com.clearing.netting.domain.model.SettlementInstruction;
import com.clearing.netting.domain.model.SettlementInstructionStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/netting-runs/{runId}/settlement-instructions")
public class SettlementInstructionController {

    private final SettlementInstructionApplicationService instructionService;

    public SettlementInstructionController(SettlementInstructionApplicationService instructionService) {
        this.instructionService = instructionService;
    }

    @GetMapping
    public InstructionSetResponse list(@PathVariable("runId") String runId) {
        AuthContext.require();
        return InstructionSetResponse.from(instructionService.listByRun(runId));
    }

    @PostMapping("/preview")
    public InstructionSetResponse preview(@PathVariable("runId") String runId) {
        AuthContext.require();
        return InstructionSetResponse.from(instructionService.preview(runId));
    }

    @PostMapping("/release")
    public InstructionSetResponse release(@PathVariable("runId") String runId) {
        AuthUser operator = AuthContext.require();
        AuthContext.requireOperator();
        return InstructionSetResponse.from(instructionService.release(runId, operator.username()));
    }

    public record InstructionResponse(
            String instructionId,
            String runId,
            String memberId,
            String memberName,
            String currency,
            BigDecimal amount,
            SettlementDirection direction,
            SettlementInstructionStatus status,
            Instant previewedAt,
            Instant releasedAt,
            String releasedBy) {
        static InstructionResponse from(SettlementInstruction i, String memberName) {
            return new InstructionResponse(
                    i.getInstructionId(),
                    i.getRunId(),
                    i.getMemberId(),
                    memberName,
                    i.getCurrency(),
                    i.getAmount(),
                    i.getDirection(),
                    i.getStatus(),
                    i.getPreviewedAt(),
                    i.getReleasedAt(),
                    i.getReleasedBy());
        }
    }

    public record RunBrief(
            String runId,
            LocalDate settleDate,
            String currency,
            NettingRunStatus status) {
        static RunBrief from(NettingRun r) {
            return new RunBrief(r.getRunId(), r.getSettleDate(), r.getCurrency(), r.getStatus());
        }
    }

    public record InstructionSetResponse(
            RunBrief run,
            List<InstructionResponse> instructions,
            boolean released) {
        static InstructionSetResponse from(InstructionSet set) {
            List<InstructionResponse> items = set.instructions().stream()
                    .map(i -> InstructionResponse.from(i, set.memberNames().get(i.getMemberId())))
                    .collect(Collectors.toList());
            boolean released = !items.isEmpty()
                    && items.stream().allMatch(i -> i.status() == SettlementInstructionStatus.RELEASED);
            return new InstructionSetResponse(RunBrief.from(set.run()), items, released);
        }
    }
}
