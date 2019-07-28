package net.viktorc.pp4j.impl;

/**
 * An exception thrown if an unexpected error occurs while running or interacting with a process.
 *
 * @author Viktor Csomor
 */
public class ProcessException extends RuntimeException {

  /**
   * Constructs a wrapper for the specified exception.
   *
   * @param e The source exception.
   */
  public ProcessException(Exception e) {
    super(e);
  }

}
