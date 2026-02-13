package com.example.payouts;

import com.example.payouts.model.dto.PayoutRequest;
import com.example.payouts.repository.PayoutRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PayoutIT {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Test
  @DisplayName("Should create payout (201) and return existing on retry (200)")
  void shouldCreatePayoutAndHandleIdempotency() throws Exception {
    String idempotencyKey = UUID.randomUUID().toString();
    PayoutRequest request =
        PayoutRequest.builder()
            .amount(new BigDecimal("50.00"))
            .currency("USD")
            .recipientAccount("acct_test_integration")
            .build();

    String requestJson = objectMapper.writeValueAsString(request);

    mockMvc
        .perform(
            post("/api/v1/payouts")
                .header("x-client-id", "default-client")
                .header("x-idempotency-key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.created").value(true))
        .andExpect(jsonPath("$.payout.status").value("PENDING"));

    mockMvc
        .perform(
            post("/api/v1/payouts")
                .header("x-client-id", "default-client")
                .header("x-idempotency-key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.created").value(false))
        .andExpect(jsonPath("$.payout.status").value("PENDING"));
  }

  @Nested
  @DisplayName("Race Condition Tests")
  class RaceConditionIT {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @MockBean private PayoutRepository payoutRepository;

    @Test
    @DisplayName("Should recover from DataIntegrityViolationException by returning existing payout")
    void shouldRecoverFromRaceCondition() throws Exception {
      String idempotencyKey = "race-condition-key";
      String clientId = "race-client";

      com.example.payouts.model.dto.PayoutRequest request =
          com.example.payouts.model.dto.PayoutRequest.builder()
              .amount(new java.math.BigDecimal("100.00"))
              .currency("USD")
              .recipientAccount("acct_existing")
              .build();

      com.example.payouts.model.Payout existingPayout =
          com.example.payouts.model.Payout.builder()
              .id(1L)
              .clientId(clientId)
              .idempotencyKey(idempotencyKey)
              .amount(new java.math.BigDecimal("100.00"))
              .currency("USD")
              .recipientAccount("acct_existing")
              .status(com.example.payouts.model.enums.PayoutStatus.PENDING)
              .build();

      Mockito.when(
              payoutRepository.findByClientIdAndIdempotencyKey(
                  ArgumentMatchers.eq(clientId), ArgumentMatchers.eq(idempotencyKey)))
          .thenReturn(java.util.Optional.empty()) // First call
          .thenReturn(java.util.Optional.of(existingPayout)); // Second call (recovery)

      Mockito.when(
              payoutRepository.save(ArgumentMatchers.any(com.example.payouts.model.Payout.class)))
          .thenThrow(
              new org.springframework.dao.DataIntegrityViolationException(
                  "Duplicate key violation"));

      mockMvc
          .perform(
              post("/api/v1/payouts")
                  .header("x-client-id", clientId)
                  .header("x-idempotency-key", idempotencyKey)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk()) // Should be 200 OK (recovered)
          .andExpect(jsonPath("$.created").value(false))
          .andExpect(jsonPath("$.payout.id").value(1))
          .andExpect(jsonPath("$.payout.status").value("PENDING"));

      Mockito.verify(payoutRepository)
          .save(ArgumentMatchers.any(com.example.payouts.model.Payout.class));
    }
  }
}
