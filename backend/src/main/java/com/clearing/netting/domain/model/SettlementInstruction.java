package com.clearing.netting.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Settlement instruction generated from one member's net position of a completed netting run.
 * Positive net amount => RECEIVE, negative => PAY. Zero net produces no instruction.
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
        if (this.amount.signum() <= 0) {
            throw new IllegalArgumentException("instruction amount must be positive");
        }
        this.direction = Objects.requireNonNull(direction);
        this.status = Objects.requireNonNull(status);
        this.previewedAt = Objects.requireNonNull(previewedAt);
        this.releasedAt = releasedAt;
        this.releasedBy = releasedBy;
    }

    public static SettlementInstruction preview(String runId, String memberId, String currency, BigDecimal netAmount) {
        BigDecimal net = Objects.requireNonNull(netAmount).setScale(8, RoundingMode.HALF_UP);
        if (net.signum() == 0) {
            throw new IllegalArgumentException("zero net position produces no instruction");
        }
        SettlementDirection direction = net.signum() > 0 ? SettlementDirection.RECEIVE : SettlementDirection.PAY;
        return new SettlementInstruction(
                UUID.randomUUID().toString(),
                runId,
                memberId,
                currency,
                net.abs(),
                direction,
                SettlementInstructionStatus.PREVIEWED,
                Instant.now(),
                null,
                null);
    }

    public void release(String operator) {
        if (this.status == SettlementInstructionStatus.RELEASED) {
            return;
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
