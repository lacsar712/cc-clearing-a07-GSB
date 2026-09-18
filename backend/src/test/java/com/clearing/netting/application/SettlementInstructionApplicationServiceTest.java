package com.clearing.netting.application;

import com.clearing.netting.domain.exception.DomainException;
import com.clearing.netting.domain.model.NetPosition;
import com.clearing.netting.domain.model.NettingRun;
import com.clearing.netting.domain.model.NettingRunStatus;
import com.clearing.netting.domain.model.SettlementDirection;
import com.clearing.netting.domain.model.SettlementInstruction;
import com.clearing.netting.domain.model.SettlementInstructionStatus;
import com.clearing.netting.domain.port.out.NetPositionRepositoryPort;
import com.clearing.netting.domain.port.out.NettingRunRepositoryPort;
import com.clearing.netting.domain.port.out.SettlementInstructionRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SettlementInstructionApplicationServiceTest {

    private NettingRunRepositoryPort runRepository;
    private NetPositionRepositoryPort positionRepository;
    private SettlementInstructionRepositoryPort instructionRepository;
    private SettlementInstructionApplicationService service;

    private static final String RUN_ID = "run-1";

    @BeforeEach
    void setUp() {
        runRepository = mock(NettingRunRepositoryPort.class);
        positionRepository = mock(NetPositionRepositoryPort.class);
        instructionRepository = mock(SettlementInstructionRepositoryPort.class);
        service = new SettlementInstructionApplicationService(runRepository, positionRepository, instructionRepository);
    }

    private NettingRun run(NettingRunStatus status) {
        return new NettingRun(RUN_ID, LocalDate.of(2026, 9, 18), "USD", status, Instant.now(), null);
    }

    @Test
    void rejectsPreviewWhenRunNotCompleted() {
        when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run(NettingRunStatus.RUNNING)));

        DomainException ex = assertThrows(DomainException.class, () -> service.preview(RUN_ID));
        assertEquals("INVALID_STATE", ex.getCode());
        verify(instructionRepository, never()).saveAll(anyList());
    }

    @Test
    void rejectsPreviewWhenRunMissing() {
        when(runRepository.findById(RUN_ID)).thenReturn(Optional.empty());

        DomainException ex = assertThrows(DomainException.class, () -> service.preview(RUN_ID));
        assertEquals("RUN_NOT_FOUND", ex.getCode());
    }

    @Test
    void previewGeneratesInstructionsFromNonZeroPositions() {
        when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run(NettingRunStatus.COMPLETED)));
        when(instructionRepository.findByRunId(RUN_ID)).thenReturn(List.of());
        when(positionRepository.findByRunId(RUN_ID)).thenReturn(List.of(
                new NetPosition("p1", RUN_ID, "A", "USD", new BigDecimal("-60")),
                new NetPosition("p2", RUN_ID, "B", "USD", new BigDecimal("40")),
                new NetPosition("p3", RUN_ID, "C", "USD", new BigDecimal("20")),
                new NetPosition("p4", RUN_ID, "D", "USD", BigDecimal.ZERO)));
        when(instructionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<SettlementInstruction> result = service.preview(RUN_ID);

        assertEquals(3, result.size());
        SettlementInstruction pay = result.stream().filter(i -> i.getMemberId().equals("A")).findFirst().orElseThrow();
        assertEquals(SettlementDirection.PAY, pay.getDirection());
        assertEquals(0, pay.getAmount().compareTo(new BigDecimal("60")), "amount is absolute value");
        SettlementInstruction receive = result.stream().filter(i -> i.getMemberId().equals("B")).findFirst().orElseThrow();
        assertEquals(SettlementDirection.RECEIVE, receive.getDirection());
        assertEquals(SettlementInstructionStatus.PREVIEWED, pay.getStatus());
    }

    @Test
    void previewIsIdempotent() {
        SettlementInstruction existing = SettlementInstruction.preview(RUN_ID, "A", "USD", new BigDecimal("10"));
        when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run(NettingRunStatus.COMPLETED)));
        when(instructionRepository.findByRunId(RUN_ID)).thenReturn(List.of(existing));

        List<SettlementInstruction> result = service.preview(RUN_ID);

        assertEquals(1, result.size());
        assertEquals(existing.getInstructionId(), result.get(0).getInstructionId());
        verify(positionRepository, never()).findByRunId(any());
        verify(instructionRepository, never()).saveAll(anyList());
    }

    @Test
    void rejectsReleaseBeforePreview() {
        when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run(NettingRunStatus.COMPLETED)));
        when(instructionRepository.findByRunId(RUN_ID)).thenReturn(List.of());

        DomainException ex = assertThrows(DomainException.class, () -> service.release(RUN_ID, "operator"));
        assertEquals("NOT_PREVIEWED", ex.getCode());
    }

    @Test
    void rejectsReleaseWhenRunNotCompleted() {
        when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run(NettingRunStatus.FAILED)));

        DomainException ex = assertThrows(DomainException.class, () -> service.release(RUN_ID, "operator"));
        assertEquals("INVALID_STATE", ex.getCode());
    }

    @Test
    void releaseMarksAllInstructionsReleasedWithOperator() {
        SettlementInstruction a = SettlementInstruction.preview(RUN_ID, "A", "USD", new BigDecimal("-60"));
        SettlementInstruction b = SettlementInstruction.preview(RUN_ID, "B", "USD", new BigDecimal("60"));
        when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run(NettingRunStatus.COMPLETED)));
        when(instructionRepository.findByRunId(RUN_ID)).thenReturn(new ArrayList<>(List.of(a, b)));
        when(instructionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<SettlementInstruction> result = service.release(RUN_ID, "operator");

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(SettlementInstruction::isReleased));
        assertEquals("operator", result.get(0).getReleasedBy());
        assertTrue(result.get(0).getReleasedAt() != null);
    }
}
