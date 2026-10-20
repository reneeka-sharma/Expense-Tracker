package com.expense.tracker;

public class Main {
    public static void main(String[] args) throws Exception {
        ApiServer server = new ApiServer(8080);
        server.start();
        System.out.println("======================================");
        System.out.println(" Expense Tracker started");
        System.out.println(" Open: http://localhost:8080");
        System.out.println("======================================");
    }
}
