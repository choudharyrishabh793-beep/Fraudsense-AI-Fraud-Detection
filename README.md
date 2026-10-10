# 🛡️ FraudSense — AI-Powered Fraud Detection System

> Real-time transaction fraud detection in pure Java: a weighted-rule AI scoring engine, role-based Swing dashboards (Admin + User), and SQLite persistence via JDBC.

![Java](https://img.shields.io/badge/Java-17-orange.svg)
![GUI](https://img.shields.io/badge/GUI-Java%20Swing-blue.svg)
![DB](https://img.shields.io/badge/DB-SQLite%20via%20JDBC-lightgrey.svg)
![Threads](https://img.shields.io/badge/Concurrency-Multithreaded-green.svg)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)

**Course:** Core Java Project — Review 1 · B.Tech CSE (AI & ML), 3rd Semester · Galgotias University
**Project Topic:** #70 — AI-powered Fraud Detection System

---

## 📌 Problem Statement

Financial fraud happens in real time, but most review systems react **after** the money is gone. Manual review is slow, rule-less systems miss patterns, and administrators have no single console to tune detection. FraudSense closes that gap: it scores every transaction the instant it occurs, alerts on suspicious activity, persists forensic evidence, and lets an administrator tune the detection algorithm live.

## ✅ Solution Overview

FraudSense monitors a live stream of financial transactions (a simulated producer thread plus manual user submissions), scores each one with a **weighted 5-rule AI engine**, classifies it as **LEGIT / REVIEW / FRAUD**, persists everything to **SQLite via JDBC**, and presents two role-based dashboards:

- **Admin Dashboard** — Detection Monitoring, Configuration Settings, Detection Reports (graphical), Algorithm Management
- **User Dashboard** — Perform Transactions, view own monitored transactions, receive real-time Fraud Alerts

---

## 🧠 How Detection Works (the "AI" core)

`RiskEngine.evaluate()` runs every transaction through five weighted rules using polymorphic dispatch over an abstract `DetectionRule` hierarchy:

| Rule | Trigger condition | Base score |
|---|---|---|
| `HIGH_AMOUNT` | amount > 50,000 (partial credit > 10,000) | 25 / 12 |
| `RAPID_VELOCITY` | ≥ 3 transactions by same user in a 5 s window | 25 / 10 |
| `HIGH_RISK_GEO` | country ∈ {RU, KP, XX} | 20 |
| `ODD_HOUR` | local hour 01:00–04:59 | 15 |
| `RISKY_CHANNEL` | card-not-present ×1.35, wire transfer ×1.5 | up to 20 |

**Verdict:** `score ≥ threshold → FRAUD` · `score ≥ 70% of threshold → REVIEW` · else `LEGIT`.

The threshold and all five rule weights are **stored in the database** and editable at runtime by the Admin (Configuration Settings / Algorithm Management tabs), including an **AUTO-TUNE FROM PERFORMANCE** action that re-tunes the threshold from the observed fraud rate.

## 🏗️ Architecture

```text
┌───────────────────────────────┐
│  TransactionSimulator         │  daemon producer thread (700 ms tick)
│  + User manual submissions    │  ConcurrentLinkedQueue buffer
└──────────────┬────────────────┘
               ▼
┌───────────────────────────────┐
│  RiskEngine (AI core)         │  synchronized evaluate()
│  5 weighted DetectionRules    │  HashMap<String,Double> weights
└──────────────┬────────────────
               ▼  verdict LEGIT / REVIEW / FRAUD
       ┌───────┴─────────
       ▼                 ▼
──────────────┐  ┌───────────────────────────┐
│DatabaseManager│  │ Observer listeners        │
│ SQLite (JDBC) │  │ AdminDashboard (4 tabs)   │
│ CRUD+settings │  │ UserDashboard (alerts)    │
└──────────────┘  └───────────────────────────┘
```

## 👥 User Types & Dashboards (Topic 70 mapping)

| Role | Login | Capabilities |
|---|---|---|
| **Admin** | `admin / admin123` | **System Configuration** — detection threshold slider + per-rule weight form with confirmation dialog · **Detection Monitoring** — live streaming table, counters, risk sparkline, on-demand fraud-burst injection · **Detection Reports** — LEGIT/REVIEW/FRAUD bar chart + detected-case table · **Algorithm Management** — editable rule weights and auto-tune from performance feedback |
| **User** | `user / user123` | **Transaction Monitoring** — submit financial transactions (amount / channel / country) that are scored live · view own transaction history · receive a real-time **FRAUD ALERTS** list with audio beep for suspicious transactions |

## 🎯 Rubric Coverage (Java GUI-Based Projects)

| Rubric item | Implementation in this codebase |
|---|---|
| **Inheritance** | `Transaction` → `CardTransaction` / `WireTransfer`; abstract `DetectionRule` → 5 rule subclasses |
| **Polymorphism** | `channel()`, `channelRiskFactor()`, `rule.score()` resolved at runtime |
| **Interfaces** | `TransactionListener`, `FraudListener` (Observer pattern wiring simulator → dashboards) |
| **Exception Handling** | custom checked `TransactionException`, `DatabaseException`; try-catch recovery in simulator & DAO |
| **Collections & Generics** | `ConcurrentLinkedQueue<Transaction>`, `HashMap<String,Double>`, generic `RingBuffer<T>`, `Comparable<Transaction>` sorting |
| **Multithreading & Synchronization** | daemon simulator thread, `synchronized` counters / `evaluate()` / `RingBuffer`, `volatile` run flag, `SwingUtilities.invokeLater` EDT marshalling |
| **Database classes** | `DatabaseManager` — schema init, CRUD, settings persistence, verdict-count & fraud-report queries |
| **JDBC** | `DriverManager`, `PreparedStatement`, `ResultSet` on every query (parameterized, injection-safe) |

## 📂 Project Structure

```text
FraudSense/
├── lib/                              # sqlite-jdbc, slf4j-api, slf4j-nop (included)
├── screenshots/                      # runtime screenshots
├── src/com/fraudsense/
│   ├── main/FraudSenseApp.java       # entry point: DB → engine → thread → login
│   ├── model/
│   │   ├── Transaction.java          # abstract base, Comparable (encapsulation)
│   │   ├── CardTransaction.java
│   │   └── WireTransfer.java
│   ├── detection/RiskEngine.java     # AI scorer + 5 DetectionRule subclasses
│   ├── simulation/TransactionSimulator.java  # producer thread + manual inject
│   ├── storage/DatabaseManager.java  # all JDBC persistence
│   ├── event/TransactionListener.java        # interface
│   ├── event/FraudListener.java              # interface
│   ├── exception/TransactionException.java
│   ├── exception/DatabaseException.java
│   ├── util/RingBuffer.java          # generic thread-safe ring buffer
│   └── gui/
│       ├── LoginFrame.java           # role-based login (Admin / User)
│       ├── AdminDashboard.java       # 4-tab admin console + bar chart + sparkline
│       └── UserDashboard.java        # transaction form + fraud alert list
├── .gitignore
└── README.md
```

## 🧰 Requirements

- **JDK 17+**
- JARs in `lib/` (already included in this repo): `sqlite-jdbc`, `slf4j-api`, `slf4j-nop`
- No internet needed at runtime; SQLite creates `fraudsense.db` automatically on first run.

## ▶️ How to Run

**PowerShell (Windows):**
```powershell
javac -cp "lib/*" -d out (Get-ChildItem -Recurse -Filter *.java src | ForEach-Object { $_.FullName })
java -cp "out;lib/*" com.fraudsense.main.FraudSenseApp
```

**Command Prompt (Windows):**
```bat
dir /s /b src\*.java > sources.txt
javac -cp "lib/*" -d out @sources.txt
java -cp "out;lib/*" com.fraudsense.main.FraudSenseApp
```

**Linux / macOS:** replace `;` with `:` in the classpath.

On launch, the login window appears — sign in as **Admin** (`admin / admin123`) or **User** (`user / user123`).

## 🗄️ Database Schema (SQLite, auto-created)

**transactions** — `id TEXT PK, type, user_id, amount REAL, country, hour, risk_score REAL, verdict, reasons, ts`
**settings** — `key TEXT PK, value REAL` (detection threshold + 5 rule weights)

## 📸 Screenshots

| Login | Admin Monitoring | Fraud Alerts |
|---|---|---|
| ![login](screenshots/01_login.png) | ![admin](screenshots/02_admin_monitoring.png) | ![alerts](screenshots/03_fraud_alerts.png) |

## 🔮 Future Scope

- Replace the simulator with a real banking API / Kafka transaction feed
- Replace weighted rules with a trained ML model (Isolation Forest / XGBoost) exported to ONNX
- Twilio SMS + email alerts on FRAUD verdicts
- Spring Boot REST layer + web frontend over the same engine
- Role-based user registration with hashed credentials

## 👥 Team

| Name | Role |
|---|---|
| **Rishabh** | Team Leader — Engine & Architecture |
| Vinay Pratap Singh | GUI & Dashboards |
| Deepika Das | Database & JDBC Layer |
| Tanya Sinha | Testing & Documentation |

## 📄 License

MIT — built for academic evaluation (Core Java Project, Review 1).
