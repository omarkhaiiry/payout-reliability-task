package com.example.payouts;

import com.example.payouts.model.dto.PayoutRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Should create payout (201) and return existing on retry (200)")
    void shouldCreatePayoutAndHandleIdempotency() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        PayoutRequest request = PayoutRequest.builder()
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
}
