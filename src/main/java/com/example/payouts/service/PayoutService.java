package com.example.payouts.service;

import com.example.payouts.model.Payout;
import com.example.payouts.repository.PayoutRepository;
import com.example.payouts.model.dto.PayoutRequest;
import com.example.payouts.model.enums.PayoutStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutService {

    private final PayoutRepository payoutRepository;
    private final PayoutQueueSender payoutQueueSender;

    // Transactional removed to allow catching DataIntegrityViolationException
    // without rollback-only mark
    public PayoutCreationResult createPayout(PayoutRequest request, String idempotencyKey, String clientId) {
        Optional<Payout> existing = findExistingPayout(clientId, idempotencyKey);
        return existing.map(payout -> new PayoutCreationResult(payout, false))
                .orElseGet(() -> attemptSave(request, idempotencyKey, clientId));
    }

    private PayoutCreationResult attemptSave(PayoutRequest request, String idempotencyKey, String clientId) {
        Payout newPayout = mapRequestToEntity(request, idempotencyKey, clientId);
        try {
            Payout saved = payoutRepository.save(newPayout);
            payoutQueueSender.send(saved);
            return new PayoutCreationResult(saved, true);
        } catch (DataIntegrityViolationException e) {
            Payout existing = recoverFromRaceCondition(clientId, idempotencyKey);
            return new PayoutCreationResult(existing, false);
        }
    }

    private Optional<Payout> findExistingPayout(String clientId, String idempotencyKey) {
        Optional<Payout> existing = payoutRepository.findByClientIdAndIdempotencyKey(clientId, idempotencyKey);
        if (existing.isPresent()) {
            log.info("Payout already exists for client: {} key: {}", clientId, idempotencyKey);
        }
        return existing;
    }

    private Payout recoverFromRaceCondition(String clientId, String idempotencyKey) {
        log.warn("Duplicate request detected for key: {}. Returning existing record.", idempotencyKey);
        return payoutRepository.findByClientIdAndIdempotencyKey(clientId, idempotencyKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Payout should exist but was not found after constraint violation. Key: " + idempotencyKey));
    }

    private Payout mapRequestToEntity(PayoutRequest request, String idempotencyKey, String clientId) {
        return Payout.builder()
                .clientId(clientId)
                .idempotencyKey(idempotencyKey)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .recipientAccount(request.getRecipientAccount())
                .status(PayoutStatus.PENDING)
                .build();
    }

    public record PayoutCreationResult(Payout payout, boolean created) {
    }
}
