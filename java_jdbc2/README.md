# HIGH-FREQUENCY ALGORITHMIC STOCK TRADING & ORDER EXECUTION ENGINE

### Project Assignment – Set 27

**Domain:** Financial Technology | Algorithmic Trading | Database Management

**Technologies:** Java, JDBC, MySQL, Multithreading, File Handling, Oracle Cloud Infrastructure (OCI)

---

## 1. Project Overview

The High-Frequency Algorithmic Stock Trading & Order Execution Engine is a Java-based database-driven application designed to simulate automated stock order processing and execution.

The system accepts BUY and SELL orders, processes market telemetry, evaluates trading strategies, monitors portfolio risk, and generates execution reports.

It uses Java JDBC to communicate with a MySQL database and Java multithreading to process multiple orders concurrently.

The application also implements a circuit breaker mechanism to prevent order execution when predefined risk or latency limits are exceeded.

**Note:** This is an educational stock market simulation. It does not connect to a real stock exchange or execute real financial transactions.

---

## 2. Project Objectives

1. Develop a Java-based stock trading simulation.
2. Store order information using MySQL and JDBC.
3. Implement three algorithmic trading strategies.
4. Process multiple orders using multithreading.
5. Validate stock ticker symbols using regular expressions.
6. Implement portfolio Value at Risk (VaR) monitoring.
7. Automatically cancel orders when risk limits are breached.
8. Generate execution reports in CSV format.
9. Demonstrate file handling and FIX protocol string processing.
10. Deploy the application and database using two OCI virtual machines.

---

## 3. Technologies Used

| Technology | Purpose |
|---|---|
| Java 17+ | Application development |
| JDBC | Database connectivity |
| MySQL 8.0 | Data storage |
| MySQL Connector/J | Java-MySQL connection |
| ExecutorService | Multithreading |
| Regex | Ticker validation |
| File I/O | Reading and writing files |
| CSV | Execution reports |
| OCI | Cloud deployment |
| VS Code | Development environment |

---

## 4. System Architecture

The project follows a two-VM architecture.

```text
             STUDENT 1
          APPLICATION HOST
              OCI VM 1
                 |
                 |
        Java Trading Engine
                 |
       +---------+----------+
       |         |          |
    Order     Strategy    Risk
  Processing  Evaluation  Manager
       |         |          |
       +---------+----------+
                 |
            JDBC Connection
                 |
          Network Port 3306
                 |
       +-------------------+
       |                   |
       |    STUDENT 2      |
       |  DATABASE HOST    |
       |     OCI VM 2      |
       |                   |
       |    MySQL Server   |
       |                   |
       +-------------------+
                 |
           Trading Database
```

### Team Responsibilities

**Student 1 – Application Host**
- Configure OCI VM 1.
- Develop the Java trading engine.
- Implement multithreading.
- Implement order matching and execution.
- Generate execution reports.

**Student 2 – Database Host**
- Configure OCI VM 2.
- Install and configure MySQL.
- Create the trading database.
- Configure remote JDBC access.
- Configure network security rules.

---

## 5. Project Folder Structure

```text
java-team/
│
├── src/
│   ├── DBConnection.java
│   ├── TradingDAO.java
│   ├── ExecutionWorker.java
│   ├── FillReportWriter.java
│   ├── MainApp.java
│   ├── Order.java
│   ├── MarketTelemetry.java
│   ├── RiskManager.java
│   └── StrategyEvaluator.java
│
├── sql/
│   └── trading_engine_schema.sql
│
├── input/
│   └── order_stream.txt
│
├── output/
│   ├── execution_fills.csv
│   └── circuit_breaker_logs.csv
│
├── reports/
│   └── sample_execution_report.txt
│
├── lib/
│   └── mysql-connector-j.jar
│
├── README.md
└── .gitignore
```

---

## 6. Software Requirements

Install the following software before running the project.

1. Java JDK 17 or above.
2. MySQL Server 8.0.
3. MySQL Workbench.
4. Visual Studio Code.
5. Java Extension Pack for VS Code.
6. MySQL Connector/J.

### Verify Java Installation

Open the VS Code terminal.

```bash
java -version
```

Check the Java compiler:

```bash
javac -version
```

### Verify MySQL Installation

Open MySQL Workbench and connect to your local MySQL server.

Execute:

```sql
SELECT VERSION();
```

---

## 7. Database Configuration

### Step 1: Open MySQL Workbench

Open MySQL Workbench and connect to your MySQL server.

### Step 2: Create the Database

Execute:

```sql
CREATE DATABASE trading_engine;
```

Select the database:

```sql
USE trading_engine;
```

### Step 3: Execute the Schema File

Open the project file:

`sql/trading_engine_schema.sql`

Execute the complete SQL script.

### Step 4: Verify Tables

```sql
SHOW TABLES;
```

The database should contain:

| Table Name | Description |
|---|---|
| orders | Stores BUY and SELL orders |
| order_book_logs | Stores market telemetry |
| executions | Stores execution information |
| risk_breaches | Stores circuit breaker events |

---

## 8. JDBC Configuration

The Java application connects to MySQL through JDBC.

Example connection URL:

```java
jdbc:mysql://localhost:3306/trading_engine
```

Configure the database username and password in `DBConnection.java` or through environment variables, depending on the implementation.

Example:

```java
String url = "jdbc:mysql://localhost:3306/trading_engine";
String user = System.getenv("DB_USER");
String password = System.getenv("DB_PASSWORD");
```

Never upload actual database passwords to GitHub.

### MySQL Connector/J

Download MySQL Connector/J from:

https://dev.mysql.com/downloads/connector/j/

Place the downloaded JAR file inside the `lib` folder.

Example:

```text
lib/mysql-connector-j.jar
```

Use the actual downloaded JAR filename in your compile command.

---

## 9. Application Execution

### Step 1: Open the Project

Open VS Code.

Select:

`File → Open Folder → java-team`

### Step 2: Open Terminal

Select:

`Terminal → New Terminal`

### Step 3: Compile Java Files

Windows PowerShell:

```powershell
javac -cp ".;lib/mysql-connector-j.jar" -d out src/*.java
```

### Step 4: Run the Application

```powershell
java -cp "out;lib/mysql-connector-j.jar" MainApp
```

**Note:** These commands assume that all Java files use the default package and `MainApp.java` contains the main method. Replace the JAR filename if yours is different.

### Expected Console Output

```text
=============================================
 HIGH-FREQUENCY ALGORITHMIC TRADING ENGINE
=============================================

1. Add New Order
2. View All Orders
3. Enter Market Telemetry
4. Execute Orders
5. Select Trading Strategy
6. Calculate Execution Quality Score
7. Check Portfolio VaR
8. View Circuit Breaker Status
9. View Execution History
10. Generate Fill Report
11. Read Order Stream File
12. Exit

Enter your choice:
```

---

## 10. Core Functional Modules

### 10.1 Order Ingestion

The application accepts the following information:

- Order ID
- Stock ticker symbol
- Order type (BUY/SELL)
- Limit price
- Quantity

Example:

```text
Order ID: ORD101
Ticker: AAPL
Order Type: BUY
Limit Price: 185.50
Quantity: 100
```

Ticker validation uses:

```java
[A-Z]{1,5}
```

Valid examples:

```text
AAPL
TSLA
INFY
TCS
```

Invalid examples:

```text
apple
123ABC
TOOLONG
```

### 10.2 Exchange Telemetry

The application records:

- Bid price
- Ask price
- Bid/Ask spread
- Network latency
- Market depth

### 10.3 Execution Audit

The application records:

- Filled price
- Execution slippage
- Commission charged
- Execution timestamp in microseconds

---

## 11. Trading Strategy Implementation

The system supports three trading strategies.

### A. Market Making Strategy

| Parameter | Weight |
|---|---:|
| Spread Capture | 40% |
| Order Latency | 40% |
| Inventory Risk | 20% |

Maximum latency: **5 ms**

### B. Statistical Arbitrage

| Parameter | Weight |
|---|---:|
| Price Divergence | 50% |
| Order Latency | 30% |
| Execution Slippage | 20% |

Maximum latency: **2 ms**

### C. High-Volatility Momentum

| Parameter | Weight |
|---|---:|
| Momentum Vector | 40% |
| Slippage | 40% |
| Commission Impact | 20% |

Maximum latency: **10 ms**

The user can select a strategy from the console menu.

---

## 12. Risk Management – Circuit Breaker

The application continuously checks portfolio risk and execution latency.

### Value at Risk Formula

\[
VaR(\%) =
\frac{\text{Potential Portfolio Loss}}
{\text{Total Portfolio Equity}}\times100
\]

### Circuit Breaker Conditions

A circuit breaker is triggered when:

1. Portfolio VaR exceeds 5%.
2. Network latency exceeds the selected strategy's maximum limit.

### Circuit Breaker Actions

- Cancel pending orders.
- Halt the account.
- Prevent further order execution.
- Store risk breach information in MySQL.
- Generate a circuit breaker CSV report.

Circuit breaker status:

```text
CIRCUIT_BREAKER_HALT
```

---

## 13. Execution Quality Score

The Execution Quality Score evaluates order execution performance using latency, slippage, and spread.

A normalized scoring model is used:

\[
EQS = 0.4L + 0.4S + 0.2P
\]

Where:

- L = Normalized latency score.
- S = Normalized slippage score.
- P = Normalized spread score.

Each component is calculated between 0 and 100.

The scoring formula is configurable and must be implemented consistently in `StrategyEvaluator.java`.

### Execution Grading

| Score | Execution Quality |
|---|---|
| 90–100 | Alpha Execution |
| 75–89 | Optimal Fill |
| 60–74 | High Slippage |
| Below 60 | Order Cancelled / Halted |

Any circuit breaker flag results in Trade Rejected, regardless of the numerical score.

---

## 14. String Processing

The project demonstrates important Java String operations.

| Method | Application |
|---|---|
| matches() | Validate ticker symbols |
| split("=") | Parse FIX protocol tags |
| indexOf() | Locate FIX tag positions |
| trim() | Clean trader IDs |
| String.format() | Format audit reports |

Example FIX message:

```text
35=D|55=AAPL|54=1|38=100
```

The application parses the FIX tags to identify the order information.

---

## 15. File Handling

The application uses Java File I/O to process market tick information.

### Input File

```text
input/order_stream.txt
```

The application reads incoming simulated market orders from this file.

### Output Files

**1. Execution Fills**

```text
output/execution_fills.csv
```

Contains:

- Order ID
- Ticker
- Order type
- Filled price
- Quantity
- Slippage
- Commission
- Timestamp

**2. Circuit Breaker Logs**

```text
output/circuit_breaker_logs.csv
```

Contains:

- Risk event ID
- Order ID
- VaR percentage
- Latency
- Breach reason
- Timestamp

---

## 16. Multithreading

The application uses Java ExecutorService to process multiple orders concurrently.

Each order is assigned to an execution worker.

Main advantages:

1. Concurrent order processing.
2. Better task management.
3. Independent order execution tasks.
4. Improved application responsiveness.

Execution logs should record:

- Thread name.
- Order ID.
- Processing start time.
- Processing completion time.
- Execution duration.

**Performance Note:** Actual sub-millisecond performance must be measured using appropriate timing methods. Console output or simulated delays must not be presented as proof of exchange-level latency.

---

## 17. Testing and Verification

Perform the following tests before submission.

| Test Case | Expected Result |
|---|---|
| Valid ticker AAPL | Accepted |
| Invalid ticker apple | Rejected |
| BUY order | Order recorded |
| SELL order | Order recorded |
| Market Making | Strategy selected |
| Statistical Arbitrage | Strategy selected |
| High-Volatility Momentum | Strategy selected |
| VaR above 5% | Circuit breaker triggered |
| Latency above limit | Circuit breaker triggered |
| Valid execution | Fill recorded |
| CSV generation | CSV file created |
| Multiple orders | Concurrent processing demonstrated |
| Invalid database connection | Proper error displayed |

---

## 18. OCI Cloud Deployment

### Student 1 – Application Host (OCI VM 1)

1. Create an OCI compute instance.
2. Install Java JDK.
3. Transfer the Java project.
4. Configure the MySQL JDBC connection.
5. Connect to the remote database.
6. Compile the Java application.
7. Execute the trading engine.
8. Verify order execution logs.

### Student 2 – Database Host (OCI VM 2)

1. Create an OCI compute instance.
2. Install MySQL Server.
3. Create the `trading_engine` database.
4. Execute the SQL schema.
5. Configure MySQL for remote access.
6. Create a dedicated database user with appropriate privileges.
7. Configure the OCI Security List or Network Security Group.
8. Allow inbound TCP port 3306 only from the application VM's private IP address.
9. Verify the remote JDBC connection.

**Security Recommendation:**

Do not expose MySQL port 3306 to the entire public internet. Use private networking where possible, restrict source IP addresses, and use secure credentials.

---

## 19. Submission Deliverables

The following items must be submitted.

| No. | Deliverable |
|---|---|
| 1 | trading_engine_schema.sql |
| 2 | DBConnection.java |
| 3 | TradingDAO.java |
| 4 | ExecutionWorker.java |
| 5 | FillReportWriter.java |
| 6 | MainApp.java |
| 7 | Additional Java support classes |
| 8 | README.md |
| 9 | OCI Security List screenshots |
| 10 | Remote JDBC connection proof |
| 11 | Order ingestion screenshots |
| 12 | Ticker regex validation proof |
| 13 | Multithreading execution logs |
| 14 | CSV execution reports |

---

## 20. Expected Project Outcome

After successful execution, the project will demonstrate:

- Database-driven order management.
- Concurrent order execution.
- Algorithmic trading strategy evaluation.
- Market telemetry processing.
- Portfolio risk monitoring.
- Automated circuit breaker functionality.
- Java String processing.
- File handling and CSV report generation.
- Remote MySQL connectivity.
- OCI cloud deployment architecture.

---

## 21. Conclusion

The High-Frequency Algorithmic Stock Trading & Order Execution Engine demonstrates the practical application of Java programming, JDBC, MySQL, multithreading, file handling, and cloud computing.

The project provides an educational simulation of automated trading workflows while demonstrating database management, execution quality evaluation, and risk control mechanisms.

It helps students understand how software engineering concepts can be applied to financial technology systems.

---

**Project:** High-Frequency Algorithmic Stock Trading & Order Execution Engine

**Assignment Set:** 27

**Application:** Java JDBC

**Database:** MySQL 8.0

**Cloud Platform:** Oracle Cloud Infrastructure (OCI)