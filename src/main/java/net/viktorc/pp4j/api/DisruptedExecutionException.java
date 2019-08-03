package net.viktorc.pp4j.api;

/**
 * An exception thrown when the execution of a submission is disrupted by an error beyond the submission.
 *
 * @author Viktor Csomor
 */
public class DisruptedExecutionException extends Exception {

  /**
   * Wraps the provided exception in a <code>DisruptedExecutionException</code>.
   *
   * @param e The cause exception.
   */
  public DisruptedExecutionException(Throwable e) {
    super(e);
  }

  /**
   * Creates a <code>DisruptedExecutionException</code> with the provided error message.
   *
   * @param message The error message describing the cause of the exception.
   */
  public DisruptedExecutionException(String message) {
    super(message);
  }

}
