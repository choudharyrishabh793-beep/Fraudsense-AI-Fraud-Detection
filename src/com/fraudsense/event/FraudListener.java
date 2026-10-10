package com.fraudsense.event;

import com.fraudsense.model.Transaction;

/** INTERFACE: implemented by dashboards to receive confirmed fraud alerts. */
public interface FraudListener {
    void onFraudDetected(Transaction t, double score, String reasons);
}