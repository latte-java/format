package com.example;

import java.util.List; // the list import

/**
 * Type-level javadoc.
 */
public class Comments {
  /** A single-line javadoc. */
  private int id; // trailing field comment

  // a standalone leading comment
  public void run() {
    int a = 1; // inline after statement
    // standalone inside body
    step();

    step(); // another trailing one
  }

  /**
   * Multi-line javadoc.
   *
   * @param x the input
   */
  public void multi(int x) {
    /* block */
    use(x);
  }
}
