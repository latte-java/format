package com.example;
public class Arrangement {
  public void withdraw(int n) {
    balance -= n;
  }

  private int balance;

  public Arrangement(int initial) {
    balance = initial;
  }

  public static final String TYPE = "checking";

  public int balance() {
    return balance;
  }

  private void audit() {
  }

  static int counter;
}
