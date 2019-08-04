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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import net.viktorc.pp4j.api.Command;
import net.viktorc.pp4j.api.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A sub-class of {@link AbstractProcessManager} for the management of process instances of the {@link JavaProcess} class.
 *
 * @param <T> A type variable implementing the {@link Runnable} and {@link Serializable} interfaces that defines the base class of the
 * startup task.
 * @author Viktor Csomor
 */
public class JavaProcessManager<T extends Runnable & Serializable> extends AbstractProcessManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(JavaProcessManager.class);

  private final T initTask;
  private final T wrapUpTask;

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
    super(builder, keepAliveTime);
    this.initTask = initTask;
    this.wrapUpTask = wrapUpTask;
  }

  @Override
  public boolean startsUpInstantly() {
    return false;
  }

  @Override
  public boolean isStartedUp(String outputLine, boolean error) {
    if (!error) {
      try {
        Object output = JavaObjectCodec.getInstance().decode(outputLine);
        return output == JavaProcess.Signal.READY;
      } catch (IOException | ClassNotFoundException | IllegalArgumentException e) {
        LOGGER.trace(e.getMessage(), e);
      }
    }
    return false;
  }

  @Override
  public Optional<Submission<?>> getInitSubmission() {
    if (initTask == null) {
      return Optional.empty();
    }
    Optional<Submission<?>> initSubmission;
    try {
      // Avoid having to have the process manager serialized.
      T initTask = this.initTask;
      initSubmission = Optional.of(new JavaSubmission<>((Callable<Serializable> & Serializable) () -> {
        initTask.run();
        return null;
      }));
    } catch (IOException e) {
      LOGGER.warn(e.getMessage(), e);
      return Optional.empty();
    }
    return initSubmission;
  }

  @Override
  public Optional<Submission<?>> getTerminationSubmission() {
    List<Command> commands = new ArrayList<>();
    if (wrapUpTask != null) {
      T wrapUpTask = this.wrapUpTask;
      try {
        Submission<?> wrapUpJavaSubmission = new JavaSubmission<>((Callable<Serializable> & Serializable) () -> {
          wrapUpTask.run();
          return null;
        });
        commands.addAll(wrapUpJavaSubmission.getCommands());
      } catch (IOException e) {
        LOGGER.warn(e.getMessage(), e);
      }
    }
    try {
      String terminationCommand = JavaObjectCodec.getInstance().encode(JavaProcess.Request.TERMINATE);
      commands.add(new SimpleCommand(terminationCommand,
          (command, outputLine) -> {
            try {
              Object output = JavaObjectCodec.getInstance().decode(outputLine);
              return output == JavaProcess.Signal.TERMINATED;
            } catch (IOException | ClassNotFoundException | IllegalArgumentException e) {
              LOGGER.trace(e.getMessage(), e);
              return false;
            }
          }));
      return Optional.of(new SimpleSubmission(commands));
    } catch (IOException e) {
      LOGGER.warn(e.getMessage(), e);
      return Optional.empty();
    }
  }

  @Override
  public Charset getEncoding() {
    return JavaObjectCodec.CHARSET;
  }

}
