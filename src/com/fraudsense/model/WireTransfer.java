package com.fraudsense.model;

import com.fraudsense.exception.TransactionException;

/** Bank wire transfer (extends Transaction => INHERITANCE). */
public class WireTransfer extends Transaction {
    private final String beneficiary;

    public WireTransfer(String id, String userId, double amount, String country, int hour,
                        String beneficiary) throws TransactionException {
        super(id, userId, amount, country, hour);
        this.beneficiary = beneficiary;
    }

    public String getBeneficiary() { return beneficiary; }

    @Override public double channelRiskFactor() { return 1.5; }
    @Override public String channel() { return "WIRE"; }
}