package com.fraudsense.gui;

import com.fraudsense.detection.RiskEngine;
import com.fraudsense.simulation.TransactionSimulator;
import com.fraudsense.storage.DatabaseManager;

import javax.swing.*;
import java.awt.*;

/** Role-based login: the two user types required by Topic 70 (Admin / User). */
public class LoginFrame extends JFrame {

    public LoginFrame(TransactionSimulator sim, RiskEngine engine, DatabaseManager db) {
        super("FRAUDSENSE | Login");
        setSize(430, 340);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        getContentPane().setBackground(new Color(0x0B, 0x12, 0x20));
        setLayout(new GridLayout(0, 1, 10, 10));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(28, 60, 28, 60));

        JLabel title = new JLabel("FRAUDSENSE", SwingConstants.CENTER);
        title.setForeground(new Color(0x3B, 0x82, 0xF6));
        title.setFont(new Font("Segoe UI", Font.BOLD, 26));

        JComboBox<String> roleBox = new JComboBox<>(new String[]{"Admin", "User"});
        JTextField userField = new JTextField("admin");
        JPasswordField passField = new JPasswordField("admin123");
        style(roleBox); style(userField); style(passField);

        JLabel hint = new JLabel("demo creds — admin/admin123 · user/user123", SwingConstants.CENTER);
        hint.setForeground(new Color(0x9C, 0xA3, 0xAF));
        hint.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        roleBox.addActionListener(e -> {
            boolean admin = "Admin".equals(roleBox.getSelectedItem());
            userField.setText(admin ? "admin" : "user");
            passField.setText(admin ? "admin123" : "user123");
        });

        JButton loginBtn = new JButton("LOGIN");
        loginBtn.setBackground(new Color(0x22, 0xC5, 0x5E));
        loginBtn.setForeground(Color.BLACK);
        loginBtn.setFocusPainted(false);
        loginBtn.addActionListener(e -> {
            String role = (String) roleBox.getSelectedItem();
            String u = userField.getText().trim();
            String p = new String(passField.getPassword());
            boolean ok = ("Admin".equals(role) && u.equals("admin") && p.equals("admin123"))
                      || ("User".equals(role) && u.equals("user") && p.equals("user123"));
            if (!ok) {
                JOptionPane.showMessageDialog(this, "Invalid credentials", "Login", JOptionPane.ERROR_MESSAGE);
                return;
            }
            dispose();
            if ("Admin".equals(role)) new AdminDashboard(sim, engine, db).setVisible(true);
            else new UserDashboard(sim, "USER01").setVisible(true);
        });

        add(title); add(roleBox); add(userField); add(passField); add(hint); add(loginBtn);
    }

    private void style(JComponent c) {
        c.setBackground(new Color(0x16, 0x20, 0x2E));
        c.setForeground(Color.WHITE);
    }
}