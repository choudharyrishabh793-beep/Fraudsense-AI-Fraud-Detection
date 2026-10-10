package com.fraudsense.gui;

import com.fraudsense.event.FraudListener;
import com.fraudsense.event.TransactionListener;
import com.fraudsense.exception.TransactionException;
import com.fraudsense.model.CardTransaction;
import com.fraudsense.model.Transaction;
import com.fraudsense.model.WireTransfer;
import com.fraudsense.simulation.TransactionSimulator;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalTime;

/**
 * USER dashboard (Topic 70 user type 2):
 * performs financial transactions and receives alerts for suspicious ones.
 */
public class UserDashboard extends JFrame implements TransactionListener, FraudListener {

    private static final Color BG = new Color(0x0B, 0x12, 0x20);
    private static final Color CARD = new Color(0x16, 0x20, 0x2E);
    private static final Color RED = new Color(0xEF, 0x44, 0x44);
    private static final Color BLUE = new Color(0x3B, 0x82, 0xF6);
    private static final Color TEXT = new Color(0xE5, 0xE7, 0xEB);

    private final TransactionSimulator sim;
    private final String userId;
    private final DefaultTableModel txModel;
    private final DefaultTableModel alertModel;
    private final JTextField amountField = new JTextField("2500", 10);
    private final JComboBox<String> channelBox = new JComboBox<>(new String[]{"CARD", "WIRE"});
    private final JComboBox<String> countryBox = new JComboBox<>(new String[]{"IN", "US", "SG", "AE", "RU"});
    private int seq = 5000;

    public UserDashboard(TransactionSimulator sim, String userId) {
        super("FRAUDSENSE | USER DASHBOARD — " + userId);
        this.sim = sim; this.userId = userId;
        sim.addTransactionListener(this);
        sim.addFraudListener(this);

        setSize(950, 720);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout(10, 10));

        txModel = new DefaultTableModel(new Object[]{"ID", "Amount", "Channel", "Country", "Risk", "Verdict"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        alertModel = new DefaultTableModel(new Object[]{"Time", "ID", "Amount", "Risk", "Triggered Rules"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        add(buildForm(), BorderLayout.NORTH);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, buildTxTable(), buildAlerts());
        split.setDividerLocation(320); split.setBackground(BG);
        add(split, BorderLayout.CENTER);
    }

    /** User function: perform a financial transaction that gets monitored for fraud. */
    private JPanel buildForm() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 12));
        p.setBackground(CARD);
        JLabel title = new JLabel("PERFORM TRANSACTION:");
        title.setForeground(TEXT); title.setFont(new Font("Segoe UI", Font.BOLD, 14));
        amountField.setBackground(BG); amountField.setForeground(TEXT);
        channelBox.setBackground(BG); channelBox.setForeground(TEXT);
        countryBox.setBackground(BG); countryBox.setForeground(TEXT);
        JButton submit = new JButton("SUBMIT TRANSACTION");
        submit.setBackground(BLUE); submit.setForeground(Color.WHITE); submit.setFocusPainted(false);
        submit.addActionListener(e -> submitTransaction());
        p.add(title);
        p.add(new JLabel("Amount:")); p.add(amountField);
        p.add(new JLabel("Channel:")); p.add(channelBox);
        p.add(new JLabel("Country:")); p.add(countryBox);
        p.add(submit);
        return p;
    }

    private void submitTransaction() {
        try {
            double amount = Double.parseDouble(amountField.getText().trim());
            String country = (String) countryBox.getSelectedItem();
            int hour = LocalTime.now().getHour();
            Transaction t;
            if ("CARD".equals(channelBox.getSelectedItem()))
                t = new CardTransaction("T" + (seq++), userId, amount, country, hour, "LocalMerchant", true);
            else
                t = new WireTransfer("T" + (seq++), userId, amount, country, hour, "BEN-" + seq);
            sim.injectManual(t);
            JOptionPane.showMessageDialog(this, "Transaction submitted and monitored.", "Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (NumberFormatException nfe) {
            JOptionPane.showMessageDialog(this, "Enter a valid amount.", "Error", JOptionPane.ERROR_MESSAGE);
        } catch (TransactionException te) {
            JOptionPane.showMessageDialog(this, te.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JScrollPane buildTxTable() {
        JTable t = new JTable(txModel); styleTable(t);
        JScrollPane sp = new JScrollPane(t);
        sp.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(BLUE), "  MY TRANSACTIONS  ",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 12), BLUE));
        return sp;
    }

    /** User function: Transaction Alerts — list of alerts for suspicious transactions. */
    private JScrollPane buildAlerts() {
        JTable t = new JTable(alertModel); styleTable(t);
        JScrollPane sp = new JScrollPane(t);
        sp.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(RED), "  FRAUD ALERTS  ",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 12), RED));
        return sp;
    }

    @Override
    public void onTransactionProcessed(Transaction t) {
        if (!userId.equals(t.getUserId())) return;
        SwingUtilities.invokeLater(() -> txModel.insertRow(0, new Object[]{t.getId(),
                String.format("%,.0f", t.getAmount()), t.channel(), t.getCountry(),
                String.format("%.1f", t.getRiskScore()), t.getVerdict()}));
    }

    @Override
    public void onFraudDetected(Transaction t, double score, String reasons) {
        if (!userId.equals(t.getUserId())) return;
        SwingUtilities.invokeLater(() -> {
            alertModel.insertRow(0, new Object[]{LocalTime.now().withNano(0), t.getId(),
                    String.format("%,.0f", t.getAmount()), String.format("%.1f", score), reasons});
            Toolkit.getDefaultToolkit().beep();
        });
    }

    private void styleTable(JTable table) {
        table.setBackground(CARD); table.setForeground(TEXT);
        table.setRowHeight(26);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.getTableHeader().setBackground(new Color(0x10, 0x18, 0x26));
        table.getTableHeader().setForeground(BLUE);
    }
}