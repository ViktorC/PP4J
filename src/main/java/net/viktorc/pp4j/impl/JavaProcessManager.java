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
import net.viktorc.pp4j.api.FailedStartupException;
import net.viktorc.pp4j.impl.JavaProcess.Response;
import net.viktorc.pp4j.impl.JavaProcess.ResponseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A sub-class of {@link AbstractProcessManager} for the management of process instances of the {@link JavaProcess} class.
 *
 * @param <T> A type variable implementing the {@link Runnable} and {@link Serializable} interfaces that defines the base class of the
 * startup task.
 * @author Viktor Csomor
 */
public class JavaProcessManager<T extends Runnable & Serializable> extends SimpleProcessManager {

  /**
   * The message the Java application launcher outputs if it fails (due to illegal arguments).
   */
  public static final String JAVA_FATAL_ERROR_MESSAGE = "Error: A fatal exception has occurred. Program will exit.";

  private static final Logger LOGGER = LoggerFactory.getLogger(JavaProcessManager.class);

  /**
   * Constructs a <code>JavaProcessManager</code> instance using the specified parameters.
   *
   * @param builder The <code>ProcessBuilder</code> to use for starting the Java processes.
   * @param initTask The task to execute in each process on startup, before the process starts accepting submissions. If it is
   * <code>null</code>, no taks are executed on startup.
   * @param wrapUpTask The task to execute in each process before it is terminated. If it is <code>null</code>, no wrap-up task is
   * executed.
   * @param keepAliveTime The number of milliseconds of idleness after which the processes should be terminated. If it is
   * <code>null</code>, the life-cycle of processes will not be limited based on idleness.
   */
  public JavaProcessManager(ProcessBuilder builder, T initTask, T wrapUpTask, Long keepAliveTime) {
    super(builder,
        JavaObjectCodec.CHARSET,
        (outputLine, outputStore) -> {
          if (outputLine.contains(JAVA_FATAL_ERROR_MESSAGE)) {
            throw new FailedStartupException(String.format("Java process startup failed: %s%n%s",
                outputStore.getJointStandardOutLines(), outputLine));
          }
          try {
            Object output = JavaObjectCodec.getInstance().decode(outputLine);
            if (output instanceof Response) {
              Response response = (Response) output;
              if (response.getType() == ResponseType.PROCESS_FAILURE) {
                throw new FailedStartupException(response.getError().orElse(null));
              }
              return response.getType() == ResponseType.STARTUP_SUCCESS;
            }
          } catch (IOException | ClassNotFoundException | IllegalArgumentException e) {
            LOGGER.trace(e.getMessage(), e);
          }
          return false;
        },
        (outputLine, outputStore) -> false,
        keepAliveTime,
        () -> initTask == null ? null : new JavaSubmission<>(initTask),
        () -> new JavaSubmission<>((Runnable & Serializable) () -> {
          try {
            if (wrapUpTask != null) {
              wrapUpTask.run();
            }
          } finally {
            JavaProcess.exit();
          }
        }));
  }

}
