package com.example.payouts.service;

import com.example.payouts.model.Payout;

public interface PayoutQueueSender {
    void send(Payout payout);
}
