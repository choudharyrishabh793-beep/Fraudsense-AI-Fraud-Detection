package com.fraudsense.detection;

import com.fraudsense.model.Transaction;

import java.util.*;

/**
 * The AI core: weighted multi-rule anomaly scorer.
 * Rules extend abstract DetectionRule => INHERITANCE + POLYMORPHISM.
 * Weights in HashMap<String,Double> => COLLECTIONS & GENERICS.
 * evaluate() synchronized => THREAD SAFETY.
 */
public class RiskEngine {
    public static final String W_AMOUNT = "amount", W_VELOCITY = "velocity",
            W_GEO = "geo", W_TIME = "time", W_CHANNEL = "channel";
    private static final long VELOCITY_WINDOW_MS = 5000;

    private final HashMap<String, Double> weights = new HashMap<>();
    private final List<DetectionRule> rules = new ArrayList<>();
    private final Map<String, Deque<Long>> velocity = new HashMap<>();
    private double threshold;

    public RiskEngine(HashMap<String, Double> weights, double threshold) {
        this.weights.putAll(weights);
        this.threshold = threshold;
        rules.add(new AmountRule());
        rules.add(new VelocityRule());
        rules.add(new GeoRule());
        rules.add(new TimeRule());
        rules.add(new ChannelRule());
    }

    public synchronized double getThreshold() { return threshold; }
    public synchronized void setThreshold(double t) { this.threshold = t; }
    public HashMap<String, Double> getWeights() { return weights; }

    /** Scores one transaction; returns immutable Report. */
    public synchronized Report evaluate(Transaction t) {
        Deque<Long> dq = velocity.computeIfAbsent(t.getUserId(), k -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        dq.addLast(now);
        while (!dq.isEmpty() && now - dq.peekFirst() > VELOCITY_WINDOW_MS) dq.pollFirst();
        int v = dq.size();

        double total = 0;
        StringBuilder reasons = new StringBuilder();
        for (DetectionRule rule : rules) {                 // polymorphic dispatch
            double s = rule.score(t, v);
            if (s > 0) {
                total += s * weights.getOrDefault(rule.key(), 0.0);
                if (reasons.length() > 0) reasons.append(" | ");
                reasons.append(rule.name());
            }
        }
        double score = Math.min(100, total);
        String verdict = score >= threshold ? "FRAUD" : (score >= threshold * 0.7 ? "REVIEW" : "LEGIT");
        return new Report(score, verdict, reasons.toString());
    }

    /** Immutable scoring result. */
    public static class Report {
        private final double score;
        private final String verdict;
        private final String reasons;
        public Report(double score, String verdict, String reasons) {
            this.score = score; this.verdict = verdict; this.reasons = reasons;
        }
        public double getScore() { return score; }
        public String getVerdict() { return verdict; }
        public String getReasons() { return reasons; }
    }

    /** ABSTRACT rule base => INHERITANCE. */
    public static abstract class DetectionRule {
        public abstract String key();
        public abstract String name();
        public abstract double score(Transaction t, int velocity);
    }

    static class AmountRule extends DetectionRule {
        public String key() { return W_AMOUNT; }
        public String name() { return "HIGH_AMOUNT"; }
        public double score(Transaction t, int v) { return t.getAmount() > 50000 ? 25 : t.getAmount() > 10000 ? 12 : 0; }
    }
    static class VelocityRule extends DetectionRule {
        public String key() { return W_VELOCITY; }
        public String name() { return "RAPID_VELOCITY"; }
        public double score(Transaction t, int v) { return v >= 3 ? 25 : v == 2 ? 10 : 0; }
    }
    static class GeoRule extends DetectionRule {
        private static final Set<String> HIGH = new HashSet<>(Arrays.asList("RU", "KP", "XX"));
        public String key() { return W_GEO; }
        public String name() { return "HIGH_RISK_GEO"; }
        public double score(Transaction t, int v) { return HIGH.contains(t.getCountry()) ? 20 : 0; }
    }
    static class TimeRule extends DetectionRule {
        public String key() { return W_TIME; }
        public String name() { return "ODD_HOUR"; }
        public double score(Transaction t, int v) { return (t.getHour() >= 1 && t.getHour() <= 4) ? 15 : 0; }
    }
    static class ChannelRule extends DetectionRule {
        public String key() { return W_CHANNEL; }
        public String name() { return "RISKY_CHANNEL"; }
        public double score(Transaction t, int v) { return (t.channelRiskFactor() - 1.0) * 40; }
    }
}