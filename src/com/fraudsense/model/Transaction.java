package com.fraudsense.model;

import com.fraudsense.exception.TransactionException;

/**
 * Abstract root of every monitored transaction => INHERITANCE base.
 * Implements Comparable<Transaction> for risk sorting => COLLECTIONS/GENERICS.
 * Private fields + getters => ENCAPSULATION.
 */
public abstract class Transaction implements Comparable<Transaction> {
    private final String id;
    private final String userId;
    private final double amount;
    private final String country;
    private final int hour;          // 0-23 local hour
    private final long timestamp;
    private double riskScore;
    private String verdict = "PENDING";

    protected Transaction(String id, String userId, double amount, String country, int hour)
            throws TransactionException {
        if (amount <= 0) throw new TransactionException("Invalid amount for " + id + ": " + amount);
        this.id = id; this.userId = userId; this.amount = amount;
        this.country = country; this.hour = hour; this.timestamp = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public double getAmount() { return amount; }
    public String getCountry() { return country; }
    public int getHour() { return hour; }
    public long getTimestamp() { return timestamp; }
    public double getRiskScore() { return riskScore; }
    public void setRiskScore(double s) { this.riskScore = s; }
    public String getVerdict() { return verdict; }
    public void setVerdict(String v) { this.verdict = v; }

    /** POLYMORPHISM: each channel supplies its own risk multiplier. */
    public abstract double channelRiskFactor();
    /** POLYMORPHISM: channel label. */
    public abstract String channel();

    /** Highest risk first. */
    @Override
    public int compareTo(Transaction other) { return Double.compare(other.riskScore, this.riskScore); }
}