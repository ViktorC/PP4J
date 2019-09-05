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

import java.nio.charset.Charset;
import java.util.Optional;
import java.util.function.Supplier;
import net.viktorc.pp4j.api.FailedStartupException;
import net.viktorc.pp4j.api.Submission;

/**
 * A simple sub-class of the {@link net.viktorc.pp4j.impl.AbstractProcessManager} class that relies on instances of functional interfaces
 * passed to its constructor to implement the {@link #startsUpInstantly()}, {@link #isStartedUp(String, boolean)},
 * {@link #getInitSubmission()}, and {@link #getTerminationSubmission()} methods.
 *
 * @author Viktor Csomor
 */
public class SimpleProcessManager extends AbstractProcessManager {

  private final StartupPredicate startupPredicate;
  private final Supplier<Submission<?>> initSubmissionSupplier;
  private final Supplier<Submission<?>> terminationSubmissionSupplier;

  /**
   * Constructs a manager for the processes created by the specified {@link ProcessBuilder}.
   *
   * @param builder The instance to build the processes with.
   * @param charset The character set to use to communicate with the managed process.
   * @param keepAliveTime The number of milliseconds after which idle processes are terminated. If it is <code>null</code>, the life span
   * of the process will not be limited.
   * @param startupPredicate The predicate to use to determine when the process can be considered started up. The two parameters are the
   * line output to the process' stream and whether that stream is the standard error or the standard out stream. If it is
   * <code>null</code>, the process is assumed to start up instantaneously.
   * @param initSubmissionSupplier The optional initial submission supplier for generating the submission to submit to the process right
   * after it started up.
   * @param terminationSubmissionSupplier The optional termination submission supplier to use to generate the submission to terminate the
   * process.
   */
  public SimpleProcessManager(ProcessBuilder builder, Charset charset, Long keepAliveTime, StartupPredicate startupPredicate,
      Supplier<Submission<?>> initSubmissionSupplier, Supplier<Submission<?>> terminationSubmissionSupplier) {
    super(builder, charset, keepAliveTime);
    this.startupPredicate = startupPredicate;
    this.initSubmissionSupplier = initSubmissionSupplier;
    this.terminationSubmissionSupplier = terminationSubmissionSupplier;
  }

  /**
   * Constructs a manager for the processes created by the specified {@link ProcessBuilder}.
   *
   * @param builder The instance to build the processes with.
   * @param charset The character set to use to communicate with the managed process.
   * @param keepAliveTime The number of milliseconds after which idle processes are terminated. If it is <code>null</code>, the life span
   * of the process will not be limited.
   * @param startupPredicate The predicate to use to determine when the process can be considered started up. The two parameters are the
   * line output to the process' stream and whether that stream is the standard error or the standard out stream. If it is
   * <code>null</code>, the process is assumed to start up instantaneously.
   */
  public SimpleProcessManager(ProcessBuilder builder, Charset charset, Long keepAliveTime, StartupPredicate startupPredicate) {
    this(builder, charset, keepAliveTime, startupPredicate, null, null);
  }

  /**
   * Constructs a manager for the processes created by the specified {@link ProcessBuilder}.
   *
   * @param builder The instance to build the processes with.
   * @param charset The character set to use to communicate with the managed process.
   * @param keepAliveTime The number of milliseconds after which idle processes are terminated. If it is <code>null</code>, the life span
   * of the process will not be limited.
   */
  public SimpleProcessManager(ProcessBuilder builder, Charset charset, Long keepAliveTime) {
    this(builder, charset, keepAliveTime, null);
  }

  /**
   * Constructs a manager for the processes created by the specified {@link ProcessBuilder}.
   *
   * @param builder The instance to build the processes with.
   * @param charset The character set to use to communicate with the managed process.
   */
  public SimpleProcessManager(ProcessBuilder builder, Charset charset) {
    this(builder, charset, null);
  }

  @Override
  public boolean startsUpInstantly() {
    return startupPredicate == null;
  }

  @Override
  public boolean isStartedUp(String outputLine, boolean error) throws FailedStartupException {
    return startupPredicate == null || startupPredicate.isStartedUp(outputLine, error);
  }

  @Override
  public Optional<Submission<?>> getInitSubmission() {
    return Optional.ofNullable(initSubmissionSupplier == null ? null : initSubmissionSupplier.get());
  }

  @Override
  public Optional<Submission<?>> getTerminationSubmission() {
    return Optional.ofNullable(terminationSubmissionSupplier == null ? null : terminationSubmissionSupplier.get());
  }

  /**
   * A bi-predicate that may throw {@link FailedStartupException} for determining when a process is started up based on its output.
   *
   * @author Viktor Csomor
   */
  @FunctionalInterface
  public interface StartupPredicate {

    /**
     * Returns whether the process is started up and ready to execute commands based on the latest line of output.
     *
     * @param outputLine The latest line output by the process.
     * @param error Whether the output was printed to the process' standard error stream.
     * @return Whether the latest line of output signals the startup of the process.
     * @throws FailedStartupException If the output denotes process failure.
     */
    boolean isStartedUp(String outputLine, boolean error) throws FailedStartupException;

  }

}