package com.clearing.netting.application;

import com.clearing.netting.domain.exception.DomainException;
import com.clearing.netting.domain.model.Member;
import com.clearing.netting.domain.model.MemberStatus;
import com.clearing.netting.domain.model.NetPosition;
import com.clearing.netting.domain.model.NettingRun;
import com.clearing.netting.domain.model.NettingRunStatus;
import com.clearing.netting.domain.model.SettlementDirection;
import com.clearing.netting.domain.model.SettlementInstruction;
import com.clearing.netting.domain.model.SettlementInstructionStatus;
import com.clearing.netting.domain.port.out.MemberRepositoryPort;
import com.clearing.netting.domain.port.out.NetPositionRepositoryPort;
import com.clearing.netting.domain.port.out.NettingRunRepositoryPort;
import com.clearing.netting.domain.port.out.SettlementInstructionRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementInstructionApplicationServiceTest {

    private FakeRunRepository runRepository;
    private FakePositionRepository positionRepository;
    private FakeInstructionRepository instructionRepository;
    private FakeMemberRepository memberRepository;
    private SettlementInstructionApplicationService service;

    @BeforeEach
    void setUp() {
        runRepository = new FakeRunRepository();
        positionRepository = new FakePositionRepository();
        instructionRepository = new FakeInstructionRepository();
        memberRepository = new FakeMemberRepository();
        service = new SettlementInstructionApplicationService(
                runRepository, positionRepository, instructionRepository, memberRepository);

        memberRepository.members.put("A", new Member("A", "Bank A", MemberStatus.ACTIVE));
        memberRepository.members.put("B", new Member("B", "Bank B", MemberStatus.ACTIVE));
        memberRepository.members.put("C", new Member("C", "Bank C", MemberStatus.ACTIVE));
    }

    @Test
    void previewOnCompletedRunShowsMembersAndAmounts() {
        seedRun("run-1", NettingRunStatus.COMPLETED);
        seedPositions("run-1", "-60", "40", "20");

        SettlementInstructionApplicationService.InstructionSet result = service.preview("run-1");

        assertEquals(3, result.instructions().size());
        assertEquals("Bank A", result.memberNames().get("A"));

        Map<String, SettlementInstruction> byMember = byMember(result.instructions());
        assertInstruction(byMember.get("A"), SettlementDirection.PAY, "60.00000000");
        assertInstruction(byMember.get("B"), SettlementDirection.RECEIVE, "40.00000000");
        assertInstruction(byMember.get("C"), SettlementDirection.RECEIVE, "20.00000000");
        assertTrue(result.instructions().stream().allMatch(i -> i.getStatus() == SettlementInstructionStatus.PREVIEWED));
    }

    @Test
    void rejectsPreviewForUnfinishedRun() {
        seedRun("run-2", NettingRunStatus.RUNNING);
        seedPositions("run-2", "10");

        DomainException ex = assertThrows(DomainException.class, () -> service.preview("run-2"));
        assertEquals("INVALID_STATE", ex.getCode());
        assertTrue(instructionRepository.store.isEmpty());
    }

    @Test
    void previewIsIdempotent() {
        seedRun("run-3", NettingRunStatus.COMPLETED);
        seedPositions("run-3", "-10", "10");

        SettlementInstructionApplicationService.InstructionSet first = service.preview("run-3");
        Instant firstPreviewedAt = first.instructions().get(0).getPreviewedAt();
        SettlementInstructionApplicationService.InstructionSet second = service.preview("run-3");

        assertEquals(first.instructions().size(), second.instructions().size());
        assertEquals(
                first.instructions().get(0).getInstructionId(),
                second.instructions().get(0).getInstructionId());
        assertEquals(firstPreviewedAt, second.instructions().get(0).getPreviewedAt());
        assertEquals(2, instructionRepository.store.size());
    }

    @Test
    void releaseBeforePreviewRejected() {
        seedRun("run-4", NettingRunStatus.COMPLETED);
        seedPositions("run-4", "-10", "10");

        DomainException ex = assertThrows(DomainException.class, () -> service.release("run-4", "op"));
        assertEquals("NO_PREVIEW", ex.getCode());
    }

    @Test
    void releaseMarksInstructionsReleasedWithOperatorAndIsIdempotent() {
        seedRun("run-5", NettingRunStatus.COMPLETED);
        seedPositions("run-5", "-60", "40", "20");

        service.preview("run-5");
        SettlementInstructionApplicationService.InstructionSet released = service.release("run-5", "operator1");

        assertEquals(3, released.instructions().size());
        assertTrue(allReleased(released.instructions()));
        for (SettlementInstruction i : released.instructions()) {
            assertEquals(SettlementInstructionStatus.RELEASED, i.getStatus());
            assertEquals("operator1", i.getReleasedBy());
            assertFalse(i.getReleasedAt() == null);
        }

        // releasing again must not fail and must not overwrite the original release metadata
        SettlementInstructionApplicationService.InstructionSet again = service.release("run-5", "operator2");
        assertTrue(allReleased(again.instructions()));
        assertTrue(again.instructions().stream().allMatch(i -> "operator1".equals(i.getReleasedBy())));
    }

    @Test
    void releaseRejectsUnknownRun() {
        DomainException ex = assertThrows(DomainException.class, () -> service.release("missing", "op"));
        assertEquals("RUN_NOT_FOUND", ex.getCode());
    }

    private void seedRun(String runId, NettingRunStatus status) {
        NettingRun run = new NettingRun(
                runId, LocalDate.of(2026, 9, 18), "USD", status, Instant.now(), null);
        runRepository.store.put(runId, run);
    }

    private void seedPositions(String runId, String... amounts) {
        String[] members = {"A", "B", "C"};
        List<NetPosition> positions = new ArrayList<>();
        for (int i = 0; i < amounts.length; i++) {
            positions.add(new NetPosition("pos-" + runId + "-" + i, runId, members[i], "USD", new BigDecimal(amounts[i])));
        }
        positionRepository.store.put(runId, positions);
    }

    private Map<String, SettlementInstruction> byMember(List<SettlementInstruction> instructions) {
        Map<String, SettlementInstruction> map = new HashMap<>();
        for (SettlementInstruction i : instructions) {
            map.put(i.getMemberId(), i);
        }
        return map;
    }

    private void assertInstruction(SettlementInstruction i, SettlementDirection direction, String amount) {
        assertEquals(direction, i.getDirection());
        assertEquals(0, i.getAmount().compareTo(new BigDecimal(amount)));
    }

    private boolean allReleased(List<SettlementInstruction> instructions) {
        return instructions.stream().allMatch(i -> i.getStatus() == SettlementInstructionStatus.RELEASED);
    }

    private static class FakeRunRepository implements NettingRunRepositoryPort {
        final Map<String, NettingRun> store = new HashMap<>();

        @Override
        public NettingRun save(NettingRun run) {
            store.put(run.getRunId(), run);
            return run;
        }

        @Override
        public Optional<NettingRun> findById(String runId) {
            return Optional.ofNullable(store.get(runId));
        }

        @Override
        public List<NettingRun> findAllOrderByCreatedAtDesc() {
            return new ArrayList<>(store.values());
        }
    }

    private static class FakePositionRepository implements NetPositionRepositoryPort {
        final Map<String, List<NetPosition>> store = new HashMap<>();

        @Override
        public List<NetPosition> saveAll(List<NetPosition> positions) {
            if (!positions.isEmpty()) {
                store.put(positions.get(0).getRunId(), new ArrayList<>(positions));
            }
            return positions;
        }

        @Override
        public List<NetPosition> findByRunId(String runId) {
            return store.getOrDefault(runId, List.of());
        }
    }

    private static class FakeInstructionRepository implements SettlementInstructionRepositoryPort {
        final Map<String, SettlementInstruction> store = new HashMap<>();

        @Override
        public List<SettlementInstruction> saveAll(List<SettlementInstruction> instructions) {
            for (SettlementInstruction i : instructions) {
                store.put(i.getInstructionId(), i);
            }
            return instructions;
        }

        @Override
        public List<SettlementInstruction> findByRunIdOrderByMemberId(String runId) {
            List<SettlementInstruction> list = store.values().stream()
                    .filter(i -> i.getRunId().equals(runId))
                    .sorted(java.util.Comparator.comparing(SettlementInstruction::getMemberId))
                    .map(i -> (SettlementInstruction) i)
                    .toList();
            return new ArrayList<>(list);
        }

        @Override
        public Optional<SettlementInstruction> findById(String instructionId) {
            return Optional.ofNullable(store.get(instructionId));
        }
    }

    private static class FakeMemberRepository implements MemberRepositoryPort {
        final Map<String, Member> members = new HashMap<>();

        @Override
        public Member save(Member member) {
            members.put(member.getMemberId(), member);
            return member;
        }

        @Override
        public Optional<Member> findById(String memberId) {
            return Optional.ofNullable(members.get(memberId));
        }

        @Override
        public List<Member> findAll() {
            return new ArrayList<>(members.values());
        }

        @Override
        public List<Member> findByIds(Iterable<String> memberIds) {
            List<Member> result = new ArrayList<>();
            for (String id : memberIds) {
                Member m = members.get(id);
                if (m != null) {
                    result.add(m);
                }
            }
            return result;
        }
    }
}
