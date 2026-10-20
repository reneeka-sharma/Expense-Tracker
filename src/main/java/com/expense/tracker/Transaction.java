package com.expense.tracker;

import java.time.LocalDate;

public abstract class Transaction {
    private int id;
    private final int userId;
    private final int categoryId;
    private final double amount;
    private final String description;
    private final LocalDate date;

    protected Transaction(int userId, int categoryId, double amount,
                          String description, LocalDate date) {
        this.userId = userId;
        this.categoryId = categoryId;
        this.amount = amount;
        this.description = description;
        this.date = date;
    }

    public abstract String getType();

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getUserId() { return userId; }
    public int getCategoryId() { return categoryId; }
    public double getAmount() { return amount; }
    public String getDescription() { return description; }
    public LocalDate getDate() { return date; }
}
