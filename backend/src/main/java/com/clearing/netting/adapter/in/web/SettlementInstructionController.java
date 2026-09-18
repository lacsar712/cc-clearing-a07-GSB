package com.clearing.netting.adapter.in.web;

import com.clearing.netting.adapter.in.web.auth.AuthContext;
import com.clearing.netting.application.MemberApplicationService;
import com.clearing.netting.application.NettingApplicationService;
import com.clearing.netting.application.SettlementInstructionApplicationService;
import com.clearing.netting.domain.model.Member;
import com.clearing.netting.domain.model.NettingRun;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/settlement-instructions")
public class SettlementInstructionController {

    private final SettlementInstructionApplicationService instructionService;
    private final NettingApplicationService nettingService;
    private final MemberApplicationService memberService;

    public SettlementInstructionController(
            SettlementInstructionApplicationService instructionService,
            NettingApplicationService nettingService,
            MemberApplicationService memberService) {
        this.instructionService = instructionService;
        this.nettingService = nettingService;
        this.memberService = memberService;
    }

    @GetMapping("/runs")
    public List<RunSummaryResponse> listRuns() {
        AuthContext.require();
        List<NettingRun> runs = nettingService.listRuns();
        return runs.stream().map(run -> RunSummaryResponse.from(run, instructionService.listByRun(run.getRunId())))
                .collect(Collectors.toList());
    }

    @GetMapping("/runs/{runId}")
    public RunDetailResponse get(@PathVariable("runId") String runId) {
        AuthContext.require();
        return detail(runId);
    }

    @PostMapping("/runs/{runId}/preview")
    public RunDetailResponse preview(@PathVariable("runId") String runId) {
        AuthContext.require();
        instructionService.preview(runId);
        return detail(runId);
    }

    @PostMapping("/runs/{runId}/release")
    public RunDetailResponse release(@PathVariable("runId") String runId) {
        AuthContext.requireOperator();
        instructionService.release(runId, AuthContext.require().username());
        return detail(runId);
    }

    private RunDetailResponse detail(String runId) {
        NettingRun run = nettingService.getRun(runId);
        List<SettlementInstruction> instructions = instructionService.listByRun(runId);
        Map<String, String> memberNames = new HashMap<>();
        for (Member m : memberService.listMembers()) {
            memberNames.put(m.getMemberId(), m.getName());
        }
        return RunDetailResponse.from(run, instructions, memberNames);
    }

    static String aggregateStatus(List<SettlementInstruction> instructions) {
        if (instructions.isEmpty()) {
            return "NONE";
        }
        boolean anyPreviewed = instructions.stream()
                .anyMatch(i -> i.getStatus() == SettlementInstructionStatus.PREVIEWED);
        boolean anyReleased = instructions.stream()
                .anyMatch(i -> i.getStatus() == SettlementInstructionStatus.RELEASED);
        if (anyReleased && !anyPreviewed) {
            return "RELEASED";
        }
        if (!anyReleased) {
            return "PREVIEWED";
        }
        return "PARTIALLY_RELEASED";
    }

    public record RunBriefResponse(
            String runId,
            LocalDate settleDate,
            String currency,
            String status,
            Instant createdAt) {
        static RunBriefResponse from(NettingRun r) {
            return new RunBriefResponse(r.getRunId(), r.getSettleDate(), r.getCurrency(),
                    r.getStatus().name(), r.getCreatedAt());
        }
    }

    public record InstructionResponse(
            String instructionId,
            String runId,
            String memberId,
            String memberName,
            String currency,
            BigDecimal amount,
            String direction,
            String status,
            Instant previewedAt,
            Instant releasedAt,
            String releasedBy) {
        static InstructionResponse from(SettlementInstruction i, Map<String, String> memberNames) {
            return new InstructionResponse(
                    i.getInstructionId(),
                    i.getRunId(),
                    i.getMemberId(),
                    memberNames.getOrDefault(i.getMemberId(), ""),
                    i.getCurrency(),
                    i.getAmount(),
                    i.getDirection().name(),
                    i.getStatus().name(),
                    i.getPreviewedAt(),
                    i.getReleasedAt(),
                    i.getReleasedBy());
        }
    }

    public record RunSummaryResponse(
            RunBriefResponse run,
            String instructionStatus,
            int instructionCount,
            int releasedCount,
            BigDecimal totalPay,
            BigDecimal totalReceive) {
        static RunSummaryResponse from(NettingRun run, List<SettlementInstruction> instructions) {
            BigDecimal pay = BigDecimal.ZERO;
            BigDecimal receive = BigDecimal.ZERO;
            int released = 0;
            for (SettlementInstruction i : instructions) {
                if (i.getDirection() == SettlementDirection.PAY) {
                    pay = pay.add(i.getAmount());
                } else {
                    receive = receive.add(i.getAmount());
                }
                if (i.getStatus() == SettlementInstructionStatus.RELEASED) {
                    released++;
                }
            }
            return new RunSummaryResponse(
                    RunBriefResponse.from(run),
                    aggregateStatus(instructions),
                    instructions.size(),
                    released,
                    pay,
                    receive);
        }
    }

    public record RunDetailResponse(
            RunBriefResponse run,
            String instructionStatus,
            List<InstructionResponse> instructions,
            BigDecimal totalPay,
            BigDecimal totalReceive) {
        static RunDetailResponse from(NettingRun run, List<SettlementInstruction> instructions,
                                     Map<String, String> memberNames) {
            BigDecimal pay = BigDecimal.ZERO;
            BigDecimal receive = BigDecimal.ZERO;
            for (SettlementInstruction i : instructions) {
                if (i.getDirection() == SettlementDirection.PAY) {
                    pay = pay.add(i.getAmount());
                } else {
                    receive = receive.add(i.getAmount());
                }
            }
            return new RunDetailResponse(
                    RunBriefResponse.from(run),
                    aggregateStatus(instructions),
                    instructions.stream()
                            .map(i -> InstructionResponse.from(i, memberNames))
                            .collect(Collectors.toList()),
                    pay,
                    receive);
        }
    }
}
