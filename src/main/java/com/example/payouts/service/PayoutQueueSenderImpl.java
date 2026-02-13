package com.example.payouts.service;

import com.example.payouts.model.Payout;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PayoutQueueSenderImpl implements PayoutQueueSender {

    @Override
    public void send(Payout payout) {
        log.info("Sending payout to queue: {}", payout);
    }
}
