/*
 * Copyright 2017 Viktor Csomor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.viktorc.pp4j.impl;

import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import net.viktorc.pp4j.api.Command;
import net.viktorc.pp4j.api.FailedCommandException;
import net.viktorc.pp4j.impl.JavaProcess.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A sub-class of {@link AbstractSubmission} for encapsulating a serializable task to execute in a separate Java process. It serializes,
 * encodes, and sends the task to the process for execution. It then monitors the standard output stream of the process for the encoded
 * return value of the task or any encoded exceptions thrown in the process while executing the task. If such an exception is found, it
 * rethrows it as a {@link FailedCommandException}. If the task completes successfully, the submission invokes the
 * {@link AbstractSubmission#setResult(Object)} method with the decoded and deserialized return value.
 *
 * @param <T> The serializable return type of the task to execute in the separate Java process.
 * @author Viktor Csomor
 */
public class JavaSubmission<T extends Serializable> extends AbstractSubmission<T> {

  private static final Logger LOGGER = LoggerFactory.getLogger(JavaSubmission.class);

  private final SerializableTask<T> task;
  private final String instruction;

  /**
   * Creates a submission for the specified task that implements both the {@link Serializable} and {@link Callable} interfaces.
   *
   * @param task The task to execute.
   * @param <S> The type of the serializable and callable task.
   */
  public <S extends Callable<T> & Serializable> JavaSubmission(S task) {
    this.task = task::call;
    try {
      instruction = JavaObjectCodec.getInstance().encode(this.task);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Creates a submission for the specified task that implements both the {@link Serializable} and {@link Runnable} interfaces.
   *
   * @param task The task to execute.
   * @param result The return value of the task that is expected to be set within the {@link Runnable#run()} method of the task.
   * @param <S> The type of the serializable and runnable task.
   */
  public <S extends Runnable & Serializable> JavaSubmission(S task, T result) {
    this((Callable<T> & Serializable) () -> {
      task.run();
      return result;
    });
  }

  /**
   * Creates a submission for the specified task that implements both the {@link Serializable} and {@link Runnable} interfaces and has no
   * return value.
   *
   * @param task The task to execute.
   * @param <S> The type of the serializable and runnable task.
   */
  public <S extends Runnable & Serializable> JavaSubmission(S task) {
    this(task, null);
  }

  /**
   * Returns the task that constitutes the submission.
   *
   * @return The task submission encapsulates.
   */
  public SerializableTask<T> getTask() {
    return task;
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<Command> getCommands() {
    return Collections.singletonList(new SimpleCommand(instruction,
        (command, outputLine) -> {
          Object output;
          try {
            output = JavaObjectCodec.getInstance().decode(outputLine);
          } catch (IOException | ClassNotFoundException | IllegalArgumentException e) {
            LOGGER.trace(e.getMessage(), e);
            return false;
          }
          if (output instanceof Response) {
            Response response = (Response) output;
            switch (response.getType()) {
              case TASK_SUCCESS:
                setResult((T) response.getResult().orElse(null));
                return true;
              case TASK_FAILURE:
                throw new FailedCommandException(command, response.getError().orElse(null));
              case PROCESS_FAILURE:
                LOGGER.error("Java process error upon task submission", response.getError().orElse(null));
                return false;
              default:
                return false;
            }
          }
          return false;
        },
        (command, outputLine) -> false));
  }

  /**
   * A serializable, runnable, and callable task with a serializable return type.
   *
   * @param <T> The serializable return type of the task.
   * @author Viktor Csomor
   */
  public interface SerializableTask<T extends Serializable> extends Callable<T>, Runnable, Serializable {

    @Override
    default void run() {
      try {
        call();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

  }

}
