# Expense Tracker — Java + HTML5/CSS3/JavaScript

This is a beginner-friendly college project using:
- Core Java + OOP
- JDBC
- Java built-in HttpServer
- HTML5
- CSS3
- Vanilla JavaScript

The MySQL database is intentionally NOT included in this ZIP.
Use the separate `ExpenseTracker_MySQL.sql` file.

## Requirements
- JDK 17+
- Maven 3.8+
- MySQL 8+
- A browser

## Project
src/main/java/com/expense/tracker/
  Main.java
  DB.java
  User.java
  Transaction.java
  Expense.java
  Income.java
  ApiServer.java

src/main/resources/static/
  index.html
  style.css
  app.js

## Configure database
The Java program reads these environment variables:
DB_URL (default jdbc:mysql://localhost:3306/expense_tracker?useSSL=false&serverTimezone=UTC)
DB_USER (default root)
DB_PASSWORD (default empty)

Example Windows PowerShell:
$env:DB_USER="root"
$env:DB_PASSWORD="your_password"

Example macOS/Linux:
export DB_USER=root
export DB_PASSWORD=your_password

## Run
1. Create the MySQL database by running ExpenseTracker_MySQL.sql.
2. Configure DB_USER and DB_PASSWORD.
3. Run:
   mvn clean package
   java -jar target/expense-tracker-1.0.0.jar
4. Open http://localhost:8080

Demo:
Email: demo@example.com
Password: demo123
