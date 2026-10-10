package com.fraudsense.gui;

import com.fraudsense.detection.RiskEngine;
import com.fraudsense.event.TransactionListener;
import com.fraudsense.exception.DatabaseException;
import com.fraudsense.model.Transaction;
import com.fraudsense.simulation.TransactionSimulator;
import com.fraudsense.storage.DatabaseManager;
import com.fraudsense.util.RingBuffer;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * ADMIN dashboard (Topic 70 user type 1).
 * Tabs: Detection Monitoring | Configuration Settings | Detection Reports | Algorithm Management.
 */
public class AdminDashboard extends JFrame implements TransactionListener {

    private static final Color BG = new Color(0x0B, 0x12, 0x20);
    private static final Color CARD = new Color(0x16, 0x20, 0x2E);
    private static final Color RED = new Color(0xEF, 0x44, 0x44);
    private static final Color GREEN = new Color(0x22, 0xC5, 0x5E);
    private static final Color AMBER = new Color(0xF5, 0x9E, 0x0B);
    private static final Color BLUE = new Color(0x3B, 0x82, 0xF6);
    private static final Color TEXT = new Color(0xE5, 0xE7, 0xEB);

    private static final String[] RULE_NAMES = {"HIGH_AMOUNT", "RAPID_VELOCITY", "HIGH_RISK_GEO", "ODD_HOUR", "RISKY_CHANNEL"};
    private static final String[] RULE_KEYS = {RiskEngine.W_AMOUNT, RiskEngine.W_VELOCITY, RiskEngine.W_GEO, RiskEngine.W_TIME, RiskEngine.W_CHANNEL};

    private final TransactionSimulator sim;
    private final RiskEngine engine;
    private final DatabaseManager db;

    private final DefaultTableModel liveModel;
    private final DefaultTableModel fraudModel;
    private final DefaultTableModel ruleModel;
    private final JLabel totalLbl = new JLabel("0");
    private final JLabel fraudLbl = new JLabel("0");
    private final JLabel flagLbl = new JLabel("0");
    private final JLabel topRiskLbl = new JLabel("-");
    private final JLabel threshLbl = new JLabel();
    private final JSlider thresholdSlider;
    private final HashMap<String, JTextField> weightFields = new HashMap<>();
    private final BarChartPanel chart = new BarChartPanel();
    private final SparkPanel spark = new SparkPanel();
    private final RingBuffer<Double> history = new RingBuffer<>(120);
    private final List<Transaction> fraudCases = new ArrayList<>();
    private int tick = 0;

    public AdminDashboard(TransactionSimulator sim, RiskEngine engine, DatabaseManager db) {
        super("FRAUDSENSE | ADMIN DASHBOARD");
        this.sim = sim; this.engine = engine; this.db = db;
        sim.addTransactionListener(this);

        setSize(1200, 800);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        getContentPane().setBackground(BG);

        thresholdSlider = new JSlider(20, 90, (int) engine.getThreshold());
        thresholdSlider.addChangeListener(e -> threshLbl.setText("Detection Threshold: " + thresholdSlider.getValue()));

        liveModel = newTable(new Object[]{"ID", "Channel", "User", "Amount", "Risk", "Verdict"});
        fraudModel = newTable(new Object[]{"ID", "Amount", "Risk Score", "Triggered Rules"});
        ruleModel = new DefaultTableModel(new Object[]{"Rule", "Parameter Key", "Weight"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return c == 2; }  // weights editable
        };

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Detection Monitoring", buildMonitoring());
        tabs.addTab("Configuration Settings", buildConfig());
        tabs.addTab("Detection Reports", buildReports());
        tabs.addTab("Algorithm Management", buildAlgorithm());
        add(tabs);
        refreshReports();
    }

    private DefaultTableModel newTable(Object[] cols) {
        return new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
    }

    /** Admin function: Detection Monitoring — live stream + counters + score sparkline. */
    private JPanel buildMonitoring() {
        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.setBackground(BG); p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JPanel stats = new JPanel(new GridLayout(1, 4, 10, 0)); stats.setBackground(BG);
        stats.add(card("TOTAL TRANSACTIONS", totalLbl, BLUE));
        stats.add(card("FRAUD DETECTED", fraudLbl, RED));
        stats.add(card("FLAGGED AMOUNT", flagLbl, AMBER));
        stats.add(card("TOP RISK CASE", topRiskLbl, GREEN));
        JTable t = new JTable(liveModel); styleTable(t);
                JButton injectBtn = new JButton("INJECT FRAUD BURST");
        injectBtn.setBackground(RED);
        injectBtn.setForeground(Color.WHITE);
        injectBtn.setFocusPainted(false);
        injectBtn.addActionListener(e -> sim.requestFraudInjection());
        JPanel northWrap = new JPanel(new BorderLayout(10, 10));
        northWrap.setBackground(BG);
        northWrap.add(stats, BorderLayout.CENTER);
        northWrap.add(injectBtn, BorderLayout.EAST);
        p.add(northWrap, BorderLayout.NORTH);
        p.add(new JScrollPane(t), BorderLayout.CENTER);
        p.add(spark, BorderLayout.SOUTH);
        return p;
    }

    /** Admin function: System Configuration — thresholds + algorithm parameters. */
    private JPanel buildConfig() {
        JPanel p = new JPanel();
        p.setBackground(BG);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));

        JLabel head = new JLabel("SYSTEM CONFIGURATION — detection thresholds & algorithm parameters");
        head.setForeground(TEXT); head.setFont(new Font("Segoe UI", Font.BOLD, 16)); head.setAlignmentX(LEFT_ALIGNMENT);
        p.add(head); p.add(Box.createVerticalStrut(15));

        threshLbl.setText("Detection Threshold: " + thresholdSlider.getValue());
        threshLbl.setForeground(AMBER); threshLbl.setAlignmentX(LEFT_ALIGNMENT);
        thresholdSlider.setBackground(CARD); thresholdSlider.setForeground(TEXT);
        thresholdSlider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40)); thresholdSlider.setAlignmentX(LEFT_ALIGNMENT);
        p.add(threshLbl); p.add(thresholdSlider); p.add(Box.createVerticalStrut(15));

        for (int i = 0; i < RULE_KEYS.length; i++) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
            row.setBackground(BG); row.setAlignmentX(LEFT_ALIGNMENT);
            JLabel l = new JLabel(RULE_NAMES[i] + "  weight (" + RULE_KEYS[i] + "):");
            l.setForeground(TEXT); l.setPreferredSize(new Dimension(280, 24));
            JTextField f = new JTextField(String.valueOf(engine.getWeights().getOrDefault(RULE_KEYS[i], 1.0)), 6);
            weightFields.put(RULE_KEYS[i], f);
            row.add(l); row.add(f);
            p.add(row);
        }
        JButton save = new JButton("SAVE CONFIGURATION");
        save.setBackground(GREEN); save.setForeground(Color.BLACK); save.setFocusPainted(false); save.setAlignmentX(LEFT_ALIGNMENT);
        save.addActionListener(e -> saveConfig());
        p.add(Box.createVerticalStrut(15)); p.add(save);
        return p;
    }

    private void saveConfig() {
        try {
            int th = thresholdSlider.getValue();
            engine.setThreshold(th);
            db.saveThreshold(th);
            for (String key : RULE_KEYS) {
                double w = Double.parseDouble(weightFields.get(key).getText().trim());
                engine.getWeights().put(key, w);
                db.saveWeight(key, w);
            }
            JOptionPane.showMessageDialog(this, "Configuration updated successfully.\nThreshold = " + th,
                    "Confirmation", JOptionPane.INFORMATION_MESSAGE);
        } catch (NumberFormatException nfe) {
            JOptionPane.showMessageDialog(this, "Weights must be numeric.", "Error", JOptionPane.ERROR_MESSAGE);
        } catch (DatabaseException de) {
            JOptionPane.showMessageDialog(this, "DB error: " + de.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Admin function: Detection Reports — graphical bar chart + fraud case table. */
    private JPanel buildReports() {
        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.setBackground(BG); p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        p.add(chart, BorderLayout.NORTH);
        JTable t = new JTable(fraudModel); styleTable(t);
        JButton refresh = new JButton("REFRESH REPORTS");
        refresh.setBackground(BLUE); refresh.setForeground(Color.WHITE); refresh.setFocusPainted(false);
        refresh.addActionListener(e -> refreshReports());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT)); south.setBackground(BG); south.add(refresh);
        p.add(new JScrollPane(t), BorderLayout.CENTER);
        p.add(south, BorderLayout.SOUTH);
        return p;
    }

    private void refreshReports() {
        try {
            chart.setCounts(db.loadVerdictCounts());
            fraudModel.setRowCount(0);
            for (Object[] row : db.fetchFraudTable(50)) fraudModel.addRow(row);
        } catch (DatabaseException ex) {
            JOptionPane.showMessageDialog(this, "Report error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Admin function: Algorithm Updates — edit weights or auto-tune from performance. */
    private JPanel buildAlgorithm() {
        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.setBackground(BG); p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        ruleModel.setRowCount(0);
        for (int i = 0; i < RULE_KEYS.length; i++)
            ruleModel.addRow(new Object[]{RULE_NAMES[i], RULE_KEYS[i], engine.getWeights().getOrDefault(RULE_KEYS[i], 1.0)});
        JTable t = new JTable(ruleModel); styleTable(t);

        JButton saveAlg = new JButton("SAVE ALGORITHM UPDATES");
        saveAlg.setBackground(GREEN); saveAlg.setForeground(Color.BLACK); saveAlg.setFocusPainted(false);
        JButton autoTune = new JButton("AUTO-TUNE FROM PERFORMANCE");
        autoTune.setBackground(AMBER); autoTune.setForeground(Color.BLACK); autoTune.setFocusPainted(false);

        saveAlg.addActionListener(e -> {
            try {
                for (int r = 0; r < ruleModel.getRowCount(); r++) {
                    String key = (String) ruleModel.getValueAt(r, 1);
                    double w = Double.parseDouble(String.valueOf(ruleModel.getValueAt(r, 2)));
                    engine.getWeights().put(key, w);
                    db.saveWeight(key, w);
                    JTextField f = weightFields.get(key);
                    if (f != null) f.setText(String.valueOf(w));
                }
                JOptionPane.showMessageDialog(this, "Detection algorithm updated successfully.",
                        "Confirmation", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Invalid weight: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        autoTune.addActionListener(e -> {
            try {
                int[] c = db.loadCounts();
                if (c[0] == 0) { JOptionPane.showMessageDialog(this, "No performance data yet.", "Auto-Tune", JOptionPane.WARNING_MESSAGE); return; }
                double rate = (double) c[1] / c[0];
                int oldTh = (int) engine.getThreshold();
                int newTh = oldTh;
                if (rate > 0.20) newTh = Math.min(90, oldTh + 5);       // too many flags -> stricter
                else if (rate < 0.05) newTh = Math.max(20, oldTh - 5);  // too few -> more sensitive
                engine.setThreshold(newTh);
                db.saveThreshold(newTh);
                thresholdSlider.setValue(newTh);
                JOptionPane.showMessageDialog(this,
                        String.format("Fraud rate = %.1f%% (%d/%d). Threshold tuned %d -> %d.", rate * 100, c[1], c[0], oldTh, newTh),
                        "Algorithm Updated", JOptionPane.INFORMATION_MESSAGE);
            } catch (DatabaseException ex) {
                JOptionPane.showMessageDialog(this, "DB error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10)); btns.setBackground(BG);
        btns.add(saveAlg); btns.add(autoTune);
        p.add(new JScrollPane(t), BorderLayout.CENTER);
        p.add(btns, BorderLayout.SOUTH);
        return p;
    }

    @Override
    public void onTransactionProcessed(Transaction t) {
        SwingUtilities.invokeLater(() -> {
            liveModel.insertRow(0, new Object[]{t.getId(), t.channel(), t.getUserId(),
                    String.format("%,.0f", t.getAmount()), String.format("%.1f", t.getRiskScore()), t.getVerdict()});
            if (liveModel.getRowCount() > 100) liveModel.removeRow(liveModel.getRowCount() - 1);
            int[] s = sim.getStats();
            totalLbl.setText(String.valueOf(s[0]));
            fraudLbl.setText(String.valueOf(s[1]));
            flagLbl.setText(String.format("%,.0f", sim.getFlaggedAmount()));
            if ("FRAUD".equals(t.getVerdict())) {
                fraudCases.add(t);
                Collections.sort(fraudCases);   // uses Comparable<Transaction>
                topRiskLbl.setText(fraudCases.get(0).getId() + " (" + String.format("%.0f", fraudCases.get(0).getRiskScore()) + ")");
                Toolkit.getDefaultToolkit().beep();
            }
            history.add(t.getRiskScore());
            spark.repaint();
            if (++tick % 25 == 0) refreshReports();
        });
    }

    private JPanel card(String title, JLabel valueLbl, Color accent) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(CARD);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(3, 0, 0, 0, accent),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        JLabel t = new JLabel(title);
        t.setForeground(new Color(0x9C, 0xA3, 0xAF)); t.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        valueLbl.setForeground(TEXT); valueLbl.setFont(new Font("Segoe UI", Font.BOLD, 22));
        p.add(t, BorderLayout.NORTH); p.add(valueLbl, BorderLayout.CENTER);
        return p;
    }

    private void styleTable(JTable table) {
        table.setBackground(CARD); table.setForeground(TEXT);
        table.setRowHeight(26);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.getTableHeader().setBackground(new Color(0x10, 0x18, 0x26));
        table.getTableHeader().setForeground(BLUE);
    }

    /** Graphical representation of detected cases (LEGIT / REVIEW / FRAUD). */
    private class BarChartPanel extends JPanel {
        private int[] counts = {0, 0, 0};
        BarChartPanel() { setPreferredSize(new Dimension(0, 220)); setBackground(CARD); }
        void setCounts(int[] c) { counts = c; repaint(); }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            String[] labels = {"LEGIT", "REVIEW", "FRAUD"};
            Color[] cols = {GREEN, AMBER, RED};
            int max = Math.max(1, Math.max(counts[0], Math.max(counts[1], counts[2])));
            int w = getWidth() / 5, x = getWidth() / 5;
            for (int i = 0; i < 3; i++) {
                int h = (int) ((counts[i] / (double) max) * (getHeight() - 70));
                g2.setColor(cols[i]);
                g2.fillRoundRect(x, getHeight() - 40 - h, w, h, 10, 10);
                g2.setColor(TEXT); g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
                g2.drawString(labels[i] + " : " + counts[i], x, getHeight() - 15);
                x += w + w / 2;
            }
            g2.dispose();
        }
    }

    /** Live risk-score sparkline fed by the generic RingBuffer. */
    private class SparkPanel extends JPanel {
        SparkPanel() { setPreferredSize(new Dimension(0, 70)); setBackground(CARD); }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            List<Double> h = history.snapshot();
            if (h.size() > 1) {
                int w = getWidth() - 40, x0 = 20, y0 = getHeight() - 12;
                g2.setColor(BLUE); g2.setStroke(new BasicStroke(2));
                for (int i = 1; i < h.size(); i++) {
                    int x1 = x0 + (int) ((i - 1) * (double) w / (h.size() - 1));
                    int x2 = x0 + (int) (i * (double) w / (h.size() - 1));
                    int y1 = y0 - (int) (h.get(i - 1) / 100 * (getHeight() - 24));
                    int y2 = y0 - (int) (h.get(i) / 100 * (getHeight() - 24));
                    g2.drawLine(x1, y1, x2, y2);
                }
            }
            g2.dispose();
        }
    }
}