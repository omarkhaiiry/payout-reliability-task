package com.example.payouts.controller;

import com.example.payouts.model.dto.PayoutRequest;
import com.example.payouts.service.PayoutService;
import com.example.payouts.service.PayoutService.PayoutCreationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payouts")
@RequiredArgsConstructor
public class PayoutController {

  private final PayoutService payoutService;

  @PostMapping
  public ResponseEntity<PayoutCreationResult> createPayout(
      @RequestHeader("x-client-id") String clientId,
      @RequestHeader("x-idempotency-key") String idempotencyKey,
      @RequestBody PayoutRequest request) {

    PayoutCreationResult result = payoutService.createPayout(request, idempotencyKey, clientId);

    if (result.created()) {
      return ResponseEntity.status(HttpStatus.CREATED).body(result);
    } else {
      return ResponseEntity.ok(result);
    }
  }
}
