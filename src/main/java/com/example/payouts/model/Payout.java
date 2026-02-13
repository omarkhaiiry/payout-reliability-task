package com.example.payouts.model;

import com.example.payouts.model.enums.PayoutStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "payouts",
    uniqueConstraints = {
      @UniqueConstraint(
          columnNames = {"client_id", "idempotency_key"},
          name = "uk_client_idempotency")
    })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payout {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String clientId;

  @Column(nullable = false)
  private String idempotencyKey;

  @Column(nullable = false)
  private BigDecimal amount;

  @Column(nullable = false)
  private String currency;

  @Column(nullable = false)
  private String recipientAccount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PayoutStatus status;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }
}
