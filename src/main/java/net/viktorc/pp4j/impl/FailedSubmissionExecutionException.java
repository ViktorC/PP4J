package net.viktorc.pp4j.impl;

/**
 * An exception thrown if an unexpected error occurs while executing a submission.
 *
 * @author Viktor Csomor
 */
public class FailedSubmissionExecutionException extends RuntimeException {

  /**
   * Constructs a wrapper for the specified exception.
   *
   * @param e The source exception.
   */
   public FailedSubmissionExecutionException(Exception e) {
    super(e);
  }

}
