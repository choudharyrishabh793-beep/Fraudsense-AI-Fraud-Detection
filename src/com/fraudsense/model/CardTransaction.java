package com.fraudsense.model;

import com.fraudsense.exception.TransactionException;

/** Card payment (extends Transaction => INHERITANCE). */
public class CardTransaction extends Transaction {
    private final String merchant;
    private final boolean cardPresent;

    public CardTransaction(String id, String userId, double amount, String country, int hour,
                           String merchant, boolean cardPresent) throws TransactionException {
        super(id, userId, amount, country, hour);
        this.merchant = merchant;
        this.cardPresent = cardPresent;
    }

    public String getMerchant() { return merchant; }

    @Override public double channelRiskFactor() { return cardPresent ? 1.0 : 1.35; }
    @Override public String channel() { return cardPresent ? "CARD" : "CARD-CNP"; }
}