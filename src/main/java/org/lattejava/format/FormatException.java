/*
 * Copyright (c) 2026, The Latte Project
 *
 * Licensed under the MIT License.
 */
package org.lattejava.format;

/**
 * Thrown when source cannot be formatted, most commonly because it cannot be parsed as valid Java.
 *
 * @author The Latte Project
 */
public class FormatException extends RuntimeException {
  public FormatException(String message) {
    super(message);
  }

  public FormatException(String message, Throwable cause) {
    super(message, cause);
  }
}
