package com.clearing.netting.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Settlement instruction derived from a completed netting run's {@link NetPosition}.
 * A positive net amount becomes a RECEIVE instruction, a negative one a PAY instruction
 * (stored with a positive absolute amount). Instructions are first created as PREVIEWED
 * and can later be RELEASED by an operator.
 */
public class SettlementInstruction {
    private final String instructionId;
    private final String runId;
    private final String memberId;
    private final String currency;
    private final BigDecimal amount;
    private final SettlementDirection direction;
    private SettlementInstructionStatus status;
    private final Instant previewedAt;
    private Instant releasedAt;
    private String releasedBy;

    public SettlementInstruction(
            String instructionId,
            String runId,
            String memberId,
            String currency,
            BigDecimal amount,
            SettlementDirection direction,
            SettlementInstructionStatus status,
            Instant previewedAt,
            Instant releasedAt,
            String releasedBy) {
        this.instructionId = Objects.requireNonNull(instructionId);
        this.runId = Objects.requireNonNull(runId);
        this.memberId = Objects.requireNonNull(memberId);
        this.currency = Objects.requireNonNull(currency).toUpperCase();
        this.amount = Objects.requireNonNull(amount).setScale(8, RoundingMode.HALF_UP);
        if (this.amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("instruction amount must be positive");
        }
        this.direction = Objects.requireNonNull(direction);
        this.status = Objects.requireNonNull(status);
        this.previewedAt = Objects.requireNonNull(previewedAt);
        this.releasedAt = releasedAt;
        this.releasedBy = releasedBy;
    }

    public static SettlementInstruction preview(String runId, NetPosition position) {
        BigDecimal net = Objects.requireNonNull(position.getNetAmount());
        if (net.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("zero net position needs no settlement instruction");
        }
        SettlementDirection direction = net.compareTo(BigDecimal.ZERO) > 0
                ? SettlementDirection.RECEIVE
                : SettlementDirection.PAY;
        return new SettlementInstruction(
                UUID.randomUUID().toString(),
                runId,
                position.getMemberId(),
                position.getCurrency(),
                net.abs(),
                direction,
                SettlementInstructionStatus.PREVIEWED,
                Instant.now(),
                null,
                null);
    }

    public void release(String operator) {
        if (status == SettlementInstructionStatus.RELEASED) {
            throw new IllegalStateException("instruction already released");
        }
        this.status = SettlementInstructionStatus.RELEASED;
        this.releasedAt = Instant.now();
        this.releasedBy = Objects.requireNonNull(operator);
    }

    public boolean isReleased() {
        return status == SettlementInstructionStatus.RELEASED;
    }

    public String getInstructionId() {
        return instructionId;
    }

    public String getRunId() {
        return runId;
    }

    public String getMemberId() {
        return memberId;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public SettlementDirection getDirection() {
        return direction;
    }

    public SettlementInstructionStatus getStatus() {
        return status;
    }

    public Instant getPreviewedAt() {
        return previewedAt;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public String getReleasedBy() {
        return releasedBy;
    }
}
