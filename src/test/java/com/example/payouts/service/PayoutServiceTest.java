package com.example.payouts.service;

import com.example.payouts.model.Payout;
import com.example.payouts.model.dto.PayoutRequest;
import com.example.payouts.model.enums.PayoutStatus;
import com.example.payouts.repository.PayoutRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Payout Creation Service")
class PayoutServiceTest {

  @Mock private PayoutRepository payoutRepository;

  @Mock private PayoutQueueSender payoutQueueSender;

  @InjectMocks private PayoutService payoutService;

  private PayoutRequest request;
  private String clientId;
  private String idempotencyKey;

  @BeforeEach
  void setUp() {
    clientId = "client-123";
    idempotencyKey = "key-abc-789";
    request =
        PayoutRequest.builder()
            .amount(new BigDecimal("100.00"))
            .currency("USD")
            .recipientAccount("acct_456")
            .build();
  }

  @Test
  @DisplayName("Should save new payout and return PayoutCreationResult(created=true)")
  void shouldSaveNewPayout() {
    when(payoutRepository.findByClientIdAndIdempotencyKey(clientId, idempotencyKey))
        .thenReturn(Optional.empty());
    when(payoutRepository.save(any(Payout.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    PayoutService.PayoutCreationResult result =
        payoutService.createPayout(request, idempotencyKey, clientId);

    assertTrue(result.created());
    assertNotNull(result.payout());
    assertEquals(clientId, result.payout().getClientId());
    assertEquals(idempotencyKey, result.payout().getIdempotencyKey());

    verify(payoutQueueSender).send(any(Payout.class));
  }

  @Test
  @DisplayName("Should return existing payout if found (created=false)")
  void shouldReturnExistingPayout() {
    Payout existingPayout =
        Payout.builder()
            .clientId(clientId)
            .idempotencyKey(idempotencyKey)
            .amount(new BigDecimal("100.00"))
            .status(PayoutStatus.PENDING)
            .build();

    when(payoutRepository.findByClientIdAndIdempotencyKey(clientId, idempotencyKey))
        .thenReturn(Optional.of(existingPayout));

    PayoutService.PayoutCreationResult result =
        payoutService.createPayout(request, idempotencyKey, clientId);

    assertFalse(result.created());
    assertEquals(existingPayout, result.payout());
    verify(payoutRepository, never()).save(any());
    verify(payoutQueueSender, never()).send(any());
  }

  @Test
  @DisplayName("Should handle race condition (ConstraintViolation) and return existing")
  void shouldHandleRaceCondition() {
    Payout existingPayout =
        Payout.builder()
            .clientId(clientId)
            .idempotencyKey(idempotencyKey)
            .status(PayoutStatus.PENDING)
            .build();

    when(payoutRepository.findByClientIdAndIdempotencyKey(clientId, idempotencyKey))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(existingPayout));

    when(payoutRepository.save(any(Payout.class)))
        .thenThrow(new DataIntegrityViolationException("Constraint violation"));

    PayoutService.PayoutCreationResult result =
        payoutService.createPayout(request, idempotencyKey, clientId);

    assertFalse(result.created());
    assertEquals(existingPayout, result.payout());
    verify(payoutQueueSender, never()).send(any());
  }
}
