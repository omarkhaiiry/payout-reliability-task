package com.example.payouts.repository;

import com.example.payouts.model.Payout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PayoutRepository extends JpaRepository<Payout, Long> {
  Optional<Payout> findByClientIdAndIdempotencyKey(String clientId, String idempotencyKey);
}
