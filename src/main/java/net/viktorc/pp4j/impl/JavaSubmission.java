package net.viktorc.pp4j.impl;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import net.viktorc.pp4j.api.Command;
import net.viktorc.pp4j.api.FailedCommandException;
import net.viktorc.pp4j.api.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of {@link Submission} for serializable {@link Callable} instances to submit in Java process. It serializes, encodes,
 * and sends the <code>Callable</code> to the process for execution. It also looks for the serialized and encoded return value of the
 * <code>Callable</code>, and for a serialized and encoded {@link Throwable} instance output to the stderr stream in case of an error.
 *
 * @param <T> The serializable return type variable of the <code>Callable</code>
 * @param <S> A serializable <code>Callable</code> instance with the return type <code>T</code>.
 * @author Viktor Csomor
 */
public class JavaSubmission<T extends Serializable, S extends Callable<T> & Serializable> implements Submission<T> {

  private static final Logger LOGGER = LoggerFactory.getLogger(JavaSubmission.class);

  private final S task;
  private final String command;
  private volatile T result;

  /**
   * Creates a submission for the specified {@link Callable}.
   *
   * @param task The task to execute.
   * @throws IOException If the encoding of the serialized task fails.
   */
  public JavaSubmission(S task) throws IOException {
    this.task = task;
    command = JavaObjectCodec.getInstance().encode(task);
  }

  /**
   * Returns the task that constitutes the submission.
   *
   * @return The task submission encapsulates.
   */
  public S getTask() {
    return task;
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<Command> getCommands() {
    return Collections.singletonList(new SimpleCommand(command,
        (command, outputLine) -> {
          Object output;
          try {
            output = JavaObjectCodec.getInstance().decode(outputLine);
          } catch (IOException | ClassNotFoundException | IllegalArgumentException e) {
            LOGGER.trace(e.getMessage(), e);
            return false;
          }
          if (output instanceof JavaProcess.Response) {
            JavaProcess.Response response = (JavaProcess.Response) output;
            if (response.isError()) {
              throw new FailedCommandException(command, (Throwable) response.getResult());
            }
            result = (T) response.getResult();
            return true;
          }
          return false;
        },
        (command, outputLine) -> {
          // It cannot happen, as stderr is redirected.
          throw new FailedCommandException(command, outputLine);
        }));
  }

  @Override
  public Optional<T> getResult() {
    return Optional.ofNullable(result);
  }

  @Override
  public String toString() {
    return task.toString();
  }

}
