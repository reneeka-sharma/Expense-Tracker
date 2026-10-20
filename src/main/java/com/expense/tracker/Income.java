package com.expense.tracker;

import java.time.LocalDate;

public class Income extends Transaction {
    public Income(int userId, int categoryId, double amount,
                  String description, LocalDate date) {
        super(userId, categoryId, amount, description, date);
    }

    @Override
    public String getType() {
        return "INCOME";
    }
}
