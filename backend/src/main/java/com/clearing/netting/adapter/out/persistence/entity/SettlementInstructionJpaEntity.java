package com.clearing.netting.adapter.out.persistence.entity;

import com.clearing.netting.domain.model.SettlementDirection;
import com.clearing.netting.domain.model.SettlementInstructionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "settlement_instructions",
        uniqueConstraints = @UniqueConstraint(name = "uk_si_run_member", columnNames = {"runId", "memberId"}))
public class SettlementInstructionJpaEntity {

    @Id
    @Column(length = 64)
    private String instructionId;

    @Column(nullable = false, length = 64)
    private String runId;

    @Column(nullable = false, length = 64)
    private String memberId;

    @Column(nullable = false, length = 8)
    private String currency;

    @Column(nullable = false, precision = 28, scale = 8)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SettlementDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SettlementInstructionStatus status;

    @Column(nullable = false)
    private Instant previewedAt;

    private Instant releasedAt;

    @Column(length = 64)
    private String releasedBy;

    public String getInstructionId() {
        return instructionId;
    }

    public void setInstructionId(String instructionId) {
        this.instructionId = instructionId;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public SettlementDirection getDirection() {
        return direction;
    }

    public void setDirection(SettlementDirection direction) {
        this.direction = direction;
    }

    public SettlementInstructionStatus getStatus() {
        return status;
    }

    public void setStatus(SettlementInstructionStatus status) {
        this.status = status;
    }

    public Instant getPreviewedAt() {
        return previewedAt;
    }

    public void setPreviewedAt(Instant previewedAt) {
        this.previewedAt = previewedAt;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public void setReleasedAt(Instant releasedAt) {
        this.releasedAt = releasedAt;
    }

    public String getReleasedBy() {
        return releasedBy;
    }

    public void setReleasedBy(String releasedBy) {
        this.releasedBy = releasedBy;
    }
}
