package com.example;

public class Arrangement {
  static int counter;
  public static final String TYPE = "checking";
  private int balance;

  public Arrangement(int initial) {
    balance = initial;
  }

  public int balance() {
    return balance;
  }

  public void withdraw(int n) {
    balance -= n;
  }

  private void audit() {
  }
}
