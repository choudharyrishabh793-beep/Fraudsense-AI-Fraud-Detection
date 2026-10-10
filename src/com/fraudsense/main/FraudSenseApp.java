package com.fraudsense.main;

import com.fraudsense.detection.RiskEngine;
import com.fraudsense.exception.DatabaseException;
import com.fraudsense.gui.LoginFrame;
import com.fraudsense.simulation.TransactionSimulator;
import com.fraudsense.storage.DatabaseManager;

import javax.swing.*;

/** Entry point: DB -> AI engine -> simulator thread -> role-based login -> Admin/User dashboards. */
public class FraudSenseApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                DatabaseManager db = new DatabaseManager("fraudsense.db");
                RiskEngine engine = new RiskEngine(db.loadWeights(), db.loadThreshold());
                TransactionSimulator sim = new TransactionSimulator(engine, db);
                new LoginFrame(sim, engine, db).setVisible(true);
                Thread t = new Thread(sim);
                t.setDaemon(true);
                t.start();
            } catch (DatabaseException e) {
                JOptionPane.showMessageDialog(null, "Database error: " + e.getMessage(),
                        "FraudSense", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}