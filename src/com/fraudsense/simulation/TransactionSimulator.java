package com.fraudsense.simulation;

import com.fraudsense.detection.RiskEngine;
import com.fraudsense.event.FraudListener;
import com.fraudsense.event.TransactionListener;
import com.fraudsense.exception.DatabaseException;
import com.fraudsense.exception.TransactionException;
import com.fraudsense.model.CardTransaction;
import com.fraudsense.model.Transaction;
import com.fraudsense.model.WireTransfer;
import com.fraudsense.storage.DatabaseManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Background producer thread => MULTITHREADING.
 * Shared counters guarded by synchronized => SYNCHRONIZATION.
 * Stream buffered in ConcurrentLinkedQueue => COLLECTIONS.
 */
public class TransactionSimulator implements Runnable {
    private final RiskEngine engine;
    private final DatabaseManager db;
    private final List<TransactionListener> txListeners = new ArrayList<>();
    private final List<FraudListener> fraudListeners = new ArrayList<>();
    private final ConcurrentLinkedQueue<Transaction> buffer = new ConcurrentLinkedQueue<>();
    private final Random rnd = new Random();

    private volatile boolean running = true;
    private int burst = 0;
    private int total = 0, fraud = 0;
    private double flaggedAmount = 0;

    private final String[] users = {"U101", "U202", "U303", "U404", "U505"};
    private final String[] countries = {"IN", "IN", "IN", "US", "SG", "AE"};
    private final String[] merchants = {"Amazon", "Flipkart", "Reliance", "Uber", "Zomato"};

    public TransactionSimulator(RiskEngine engine, DatabaseManager db) {
        this.engine = engine; this.db = db;
    }

    public void addTransactionListener(TransactionListener l) { txListeners.add(l); }
    public void addFraudListener(FraudListener l) { fraudListeners.add(l); }
    public void setRunning(boolean r) { this.running = r; }
    public synchronized void requestFraudInjection() { burst = 4; }
    public synchronized int[] getStats() { return new int[]{total, fraud}; }
    public synchronized double getFlaggedAmount() { return flaggedAmount; }
    public ConcurrentLinkedQueue<Transaction> getBuffer() { return buffer; }

    @Override
    public void run() {
        Thread.currentThread().setName("FraudSense-Simulator");
        int id = 1000;
        while (!Thread.currentThread().isInterrupted()) {
            if (running) {
                try {
                    process(nextTransaction(++id));
                } catch (TransactionException | DatabaseException ex) {
                    System.err.println("[simulator] " + ex.getMessage());   // EXCEPTION HANDLING
                }
            }
            try { Thread.sleep(700); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    /** Processes a manually submitted user transaction through the same pipeline. */
    public void injectManual(Transaction t) {
        try {
            process(t);
        } catch (DatabaseException ex) {
            System.err.println("[manual] " + ex.getMessage());
        }
    }

    /** Shared pipeline: score -> persist -> notify listeners. */
    private void process(Transaction t) throws DatabaseException {
        RiskEngine.Report rep = engine.evaluate(t);
        t.setRiskScore(rep.getScore());
        t.setVerdict(rep.getVerdict());
        buffer.add(t);
        db.insertTransaction(t, rep);
        synchronized (this) {
            total++;
            if ("FRAUD".equals(rep.getVerdict())) { fraud++; flaggedAmount += t.getAmount(); }
        }
        for (TransactionListener l : txListeners) l.onTransactionProcessed(t);
        if ("FRAUD".equals(rep.getVerdict()))
            for (FraudListener l : fraudListeners) l.onFraudDetected(t, rep.getScore(), rep.getReasons());
    }

        private Transaction nextTransaction(int id) throws TransactionException {
        if (burst > 0) {                      // on-demand demo burst
            burst--;
            return fraudTransaction(id, "U999");
        }
        if (rnd.nextInt(100) < 8) {           // ~8% natural fraud wave
            return fraudTransaction(id, users[rnd.nextInt(users.length)]);
        }
        String user = users[rnd.nextInt(users.length)];
        String country = countries[rnd.nextInt(countries.length)];
        int hour = rnd.nextInt(24);
        double amount = 200 + rnd.nextInt(9000);
        return rnd.nextBoolean()
                ? new CardTransaction("T" + id, user, amount, country, hour, merchants[rnd.nextInt(merchants.length)], rnd.nextBoolean())
                : new WireTransfer("T" + id, user, amount, country, hour, "BEN-" + id);
    }

    /** Fraudulent pattern: huge amount + high-risk geo + odd hour. */
    private Transaction fraudTransaction(int id, String user) throws TransactionException {
        return new WireTransfer("T" + id, user, 60000 + rnd.nextInt(140000),
                rnd.nextBoolean() ? "RU" : "KP", 2 + rnd.nextInt(3), "ShellCorp-" + id);
    }