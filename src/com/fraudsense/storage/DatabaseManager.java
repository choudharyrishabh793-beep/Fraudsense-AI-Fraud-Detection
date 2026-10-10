package com.fraudsense.storage;

import com.fraudsense.detection.RiskEngine;
import com.fraudsense.exception.DatabaseException;
import com.fraudsense.model.Transaction;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * All persistence via JDBC PreparedStatement + ResultSet.
 * SQLException wrapped in custom DatabaseException => EXCEPTION HANDLING.
 */
public class DatabaseManager {
    private final Connection conn;

    public DatabaseManager(String dbPath) throws DatabaseException {
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            initSchema();
        } catch (SQLException e) {
            throw new DatabaseException("Cannot open SQLite database: " + dbPath, e);
        }
    }

    private void initSchema() throws DatabaseException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS transactions (" +
                    "id TEXT PRIMARY KEY, type TEXT, user_id TEXT, amount REAL, country TEXT, hour INTEGER," +
                    " risk_score REAL, verdict TEXT, reasons TEXT, ts INTEGER)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value REAL)");
            seedIfMissing(RiskEngine.W_AMOUNT, 1.0);
            seedIfMissing(RiskEngine.W_VELOCITY, 1.0);
            seedIfMissing(RiskEngine.W_GEO, 1.0);
            seedIfMissing(RiskEngine.W_TIME, 1.0);
            seedIfMissing(RiskEngine.W_CHANNEL, 1.0);
            seedIfMissing("threshold", 55.0);
        } catch (SQLException e) {
            throw new DatabaseException("Schema initialisation failed", e);
        }
    }

    private void seedIfMissing(String key, double value) throws SQLException {
        try (PreparedStatement q = conn.prepareStatement("SELECT 1 FROM settings WHERE key=?")) {
            q.setString(1, key);
            if (!q.executeQuery().next()) {
                try (PreparedStatement in = conn.prepareStatement("INSERT INTO settings(key,value) VALUES(?,?)")) {
                    in.setString(1, key); in.setDouble(2, value); in.executeUpdate();
                }
            }
        }
    }

    public void insertTransaction(Transaction t, RiskEngine.Report rep) throws DatabaseException {
        String sql = "INSERT OR REPLACE INTO transactions VALUES (?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, t.getId());
            ps.setString(2, t.channel());
            ps.setString(3, t.getUserId());
            ps.setDouble(4, t.getAmount());
            ps.setString(5, t.getCountry());
            ps.setInt(6, t.getHour());
            ps.setDouble(7, rep.getScore());
            ps.setString(8, rep.getVerdict());
            ps.setString(9, rep.getReasons());
            ps.setLong(10, t.getTimestamp());
            ps.executeUpdate();
        } catch (SQLException e) { throw new DatabaseException("INSERT failed", e); }
    }

    public HashMap<String, Double> loadWeights() throws DatabaseException {
        HashMap<String, Double> map = new HashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT key, value FROM settings WHERE key <> 'threshold'")) {
            while (rs.next()) map.put(rs.getString("key"), rs.getDouble("value"));
        } catch (SQLException e) { throw new DatabaseException("Weight load failed", e); }
        return map;
    }

    public double loadThreshold() throws DatabaseException { return readSetting("threshold"); }
    public void saveThreshold(double v) throws DatabaseException { writeSetting("threshold", v); }
    public void saveWeight(String key, double v) throws DatabaseException { writeSetting(key, v); }

    public int[] loadCounts() throws DatabaseException {
        String sql = "SELECT COUNT(*) AS c, SUM(CASE WHEN verdict='FRAUD' THEN 1 ELSE 0 END) AS f FROM transactions";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) return new int[]{rs.getInt("c"), rs.getInt("f")};
            return new int[]{0, 0};
        } catch (SQLException e) { throw new DatabaseException("Count query failed", e); }
    }

    /** Counts per verdict for the graphical report (LEGIT, REVIEW, FRAUD). */
    public int[] loadVerdictCounts() throws DatabaseException {
        String sql = "SELECT IFNULL(SUM(CASE WHEN verdict='LEGIT' THEN 1 ELSE 0 END),0) AS l, " +
                     "IFNULL(SUM(CASE WHEN verdict='REVIEW' THEN 1 ELSE 0 END),0) AS r, " +
                     "IFNULL(SUM(CASE WHEN verdict='FRAUD' THEN 1 ELSE 0 END),0) AS f FROM transactions";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) return new int[]{rs.getInt("l"), rs.getInt("r"), rs.getInt("f")};
            return new int[]{0, 0, 0};
        } catch (SQLException e) { throw new DatabaseException("Verdict counts failed", e); }
    }

    /** Rows for the detected-fraud report table. */
    public Object[][] fetchFraudTable(int limit) throws DatabaseException {
        List<Object[]> rows = new ArrayList<>();
        String sql = "SELECT id, amount, risk_score, reasons FROM transactions WHERE verdict='FRAUD' ORDER BY ts DESC LIMIT ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(new Object[]{rs.getString("id"), rs.getDouble("amount"),
                        rs.getDouble("risk_score"), rs.getString("reasons")});
            }
        } catch (SQLException e) { throw new DatabaseException("Fraud table query failed", e); }
        return rows.toArray(new Object[0][]);
    }

    private double readSetting(String key) throws DatabaseException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT value FROM settings WHERE key=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble("value") : 0;
            }
        } catch (SQLException e) { throw new DatabaseException("Setting read failed", e); }
    }

    private void writeSetting(String key, double v) throws DatabaseException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO settings(key,value) VALUES(?,?)")) {
            ps.setString(1, key); ps.setDouble(2, v); ps.executeUpdate();
        } catch (SQLException e) { throw new DatabaseException("Setting write failed", e); }
    }
}