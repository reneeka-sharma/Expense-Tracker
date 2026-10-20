package com.expense.tracker;

import java.time.LocalDate;

public class Expense extends Transaction {
    public Expense(int userId, int categoryId, double amount,
                   String description, LocalDate date) {
        super(userId, categoryId, amount, description, date);
    }

    @Override
    public String getType() {
        return "EXPENSE";
    }
}
