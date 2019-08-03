package net.viktorc.pp4j.api;

/**
 * An exception thrown when a command executed by a process fails.
 *
 * @author Viktor Csomor
 */
public class FailedCommandException extends Exception {

  /**
   * Wraps the provided exception in a <code>FailedCommandException</code>.
   *
   * @param command The failed command.
   * @param e The cause exception.
   */
  public FailedCommandException(Command command, Throwable e) {
    super(getBaseErrorMessage(command), e);
  }

  /**
   * Creates a <code>FailedCommandException</code> for the specified command with the provided error message.
   *
   * @param command The failed command.
   * @param message The reason of the failure.
   */
  public FailedCommandException(Command command, String message) {
    super(String.format("%s with reason: %s", getBaseErrorMessage(command), message));
  }

  /**
   * Creates a <code>FailedCommandException</code> for the specified command.
   *
   * @param command The failed command.
   */
  public FailedCommandException(Command command) {
    super(getBaseErrorMessage(command));
  }

  /**
   * Returns the base error message containing the command instruction.
   *
   * @param command The failed command.
   * @return The base error message.
   */
  private static String getBaseErrorMessage(Command command) {
    return String.format("Execution of command \"%s\" failed with", command.getInstruction());
  }

}
