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

  private final boolean startsUpInstantly;
  private final ProcessStartupPredicate startupPredicateStdOut;
  private final ProcessStartupPredicate startupPredicateStdErr;
  private final Supplier<Submission<?>> initSubmissionSupplier;
  private final Supplier<Submission<?>> terminationSubmissionSupplier;

  /**
   * Constructs a manager for the processes created by the specified {@link ProcessBuilder} assuming that the process does not start up
   * instantly and its startup is determined by the startup predicates.
   *
   * @param builder The instance to build the processes with.
   * @param charset The character set to use to communicate with the managed process.
   * @param startupPredicateStdOut The predicate to use to determine when the process can be considered started up based on its output
   * to its standard out stream.
   * @param startupPredicateStdErr The predicate to use to determine when the process can be considered started up based on its output
   * to its standard error stream.
   * @param keepAliveTime The number of milliseconds after which idle processes are terminated. If it is <code>null</code>, the life span
   * of the process will not be limited.
   * @param initSubmissionSupplier The optional initial submission supplier for generating the submission to submit to the process right
   * after it started up.
   * @param terminationSubmissionSupplier The optional termination submission supplier to use to generate the submission to terminate the
   * process.
   */
  public SimpleProcessManager(ProcessBuilder builder, Charset charset, ProcessStartupPredicate startupPredicateStdOut,
      ProcessStartupPredicate startupPredicateStdErr, Long keepAliveTime, Supplier<Submission<?>> initSubmissionSupplier,
      Supplier<Submission<?>> terminationSubmissionSupplier) {
    super(builder, charset, keepAliveTime);
    if (startupPredicateStdOut == null || startupPredicateStdErr == null) {
      throw new IllegalArgumentException("The startup predicates cannot be null");
    }
    startsUpInstantly = false;
    this.startupPredicateStdOut = startupPredicateStdOut;
    this.startupPredicateStdErr = startupPredicateStdErr;
    this.initSubmissionSupplier = initSubmissionSupplier;
    this.terminationSubmissionSupplier = terminationSubmissionSupplier;
  }

  /**
   * Constructs a manager for the processes created by the specified {@link ProcessBuilder} assuming that the process does not start up
   * instantly and its startup is determined by the provided startup predicate. The constructed instance throws a
   * <code>FailedStartupException</code> if anything is printed to the process' standard error during startup process.
   *
   * @param builder The instance to build the processes with.
   * @param charset The character set to use to communicate with the managed process.
   * @param startupPredicateStdOut The predicate to use to determine when the process can be considered started up based on its output
   * to its standard out stream.
   * @param keepAliveTime The number of milliseconds after which idle processes are terminated. If it is <code>null</code>, the life span
   * of the process will not be limited.
   * @param initSubmissionSupplier The optional initial submission supplier for generating the submission to submit to the process right
   * after it started up.
   * @param terminationSubmissionSupplier The optional termination submission supplier to use to generate the submission to terminate the
   * process.
   */
  public SimpleProcessManager(ProcessBuilder builder, Charset charset, ProcessStartupPredicate startupPredicateStdOut, Long keepAliveTime,
      Supplier<Submission<?>> initSubmissionSupplier, Supplier<Submission<?>> terminationSubmissionSupplier) {
    this(builder, charset, startupPredicateStdOut, (outputLine, startupOutputStore) -> {
      throw new FailedStartupException(String.format("Startup failure indicated by %s printed to stderr", outputLine));
    }, keepAliveTime, initSubmissionSupplier, terminationSubmissionSupplier);
  }

  /**
   * Constructs a manager for the processes created by the specified {@link ProcessBuilder} assuming that the process starts up instantly.
   *
   * @param builder The instance to build the processes with.
   * @param charset The character set to use to communicate with the managed process.
   * @param keepAliveTime The number of milliseconds after which idle processes are terminated. If it is <code>null</code>, the life span
   * of the process will not be limited.
   * @param initSubmissionSupplier The optional initial submission supplier for generating the submission to submit to the process right
   * after it started up.
   * @param terminationSubmissionSupplier The optional termination submission supplier to use to generate the submission to terminate the
   * process.
   */
  public SimpleProcessManager(ProcessBuilder builder, Charset charset, Long keepAliveTime, Supplier<Submission<?>> initSubmissionSupplier,
      Supplier<Submission<?>> terminationSubmissionSupplier) {
    super(builder, charset, keepAliveTime);
    startsUpInstantly = true;
    this.startupPredicateStdOut = null;
    this.startupPredicateStdErr = null;
    this.initSubmissionSupplier = initSubmissionSupplier;
    this.terminationSubmissionSupplier = terminationSubmissionSupplier;
  }

  /**
   * Constructs a manager for the processes created by the specified {@link ProcessBuilder} assuming that the process does not start up
   * instantly and its startup is determined by the startup predicates.
   *
   * @param builder The instance to build the processes with.
   * @param charset The character set to use to communicate with the managed process.
   * @param startupPredicateStdOut The predicate to use to determine when the process can be considered started up based on its output
   * to its standard out stream.
   * @param startupPredicateStdErr The predicate to use to determine when the process can be considered started up based on its output
   * to its standard error stream.
   * @param keepAliveTime The number of milliseconds after which idle processes are terminated. If it is <code>null</code>, the life span
   * of the process will not be limited.
   */
  public SimpleProcessManager(ProcessBuilder builder, Charset charset, ProcessStartupPredicate startupPredicateStdOut,
      ProcessStartupPredicate startupPredicateStdErr, Long keepAliveTime) {
    this(builder, charset, startupPredicateStdOut, startupPredicateStdErr, keepAliveTime, null, null);
  }

  /**
   * Constructs a manager for the processes created by the specified {@link ProcessBuilder} assuming that the process does not start up
   * instantly and its startup is determined by the provided startup predicate. The constructed instance throws a
   * <code>FailedStartupException</code> if anything is printed to the process' standard error during startup process.
   *
   * @param builder The instance to build the processes with.
   * @param charset The character set to use to communicate with the managed process.
   * @param startupPredicateStdOut The predicate to use to determine when the process can be considered started up based on its output
   * to its standard out stream.
   * @param keepAliveTime The number of milliseconds after which idle processes are terminated. If it is <code>null</code>, the life span
   * of the process will not be limited.
   */
  public SimpleProcessManager(ProcessBuilder builder, Charset charset, ProcessStartupPredicate startupPredicateStdOut, Long keepAliveTime) {
    this(builder, charset, startupPredicateStdOut, keepAliveTime, null, null);
  }

  /**
   * Constructs a manager for the processes created by the specified {@link ProcessBuilder} assuming that the process starts up instantly.
   *
   * @param builder The instance to build the processes with.
   * @param charset The character set to use to communicate with the managed process.
   * @param keepAliveTime The number of milliseconds after which idle processes are terminated. If it is <code>null</code>, the life span
   * of the process will not be limited.
   */
  public SimpleProcessManager(ProcessBuilder builder, Charset charset, Long keepAliveTime) {
    this(builder, charset, keepAliveTime, null, null);
  }

  /**
   * Constructs a manager for the processes created by the specified {@link ProcessBuilder} assuming that the process does not start up
   * instantly and its startup is determined by the startup predicates.
   *
   * @param builder The instance to build the processes with.
   * @param charset The character set to use to communicate with the managed process.
   * @param startupPredicateStdOut The predicate to use to determine when the process can be considered started up based on its output
   * to its standard out stream.
   * @param startupPredicateStdErr The predicate to use to determine when the process can be considered started up based on its output
   * to its standard error stream.
   */
  public SimpleProcessManager(ProcessBuilder builder, Charset charset, ProcessStartupPredicate startupPredicateStdOut,
      ProcessStartupPredicate startupPredicateStdErr) {
    this(builder, charset, startupPredicateStdOut, startupPredicateStdErr, null, null, null);
  }

  /**
   * Constructs a manager for the processes created by the specified {@link ProcessBuilder} assuming that the process does not start up
   * instantly and its startup is determined by the provided startup predicate. The constructed instance throws a
   * <code>FailedStartupException</code> if anything is printed to the process' standard error during startup process.
   *
   * @param builder The instance to build the processes with.
   * @param charset The character set to use to communicate with the managed process.
   * @param startupPredicateStdOut The predicate to use to determine when the process can be considered started up based on its output
   * to its standard out stream.
   */
  public SimpleProcessManager(ProcessBuilder builder, Charset charset, ProcessStartupPredicate startupPredicateStdOut) {
    this(builder, charset, startupPredicateStdOut, null, null, null);
  }

  /**
   * Constructs a manager for the processes created by the specified {@link ProcessBuilder} assuming that the process starts up instantly.
   *
   * @param builder The instance to build the processes with.
   * @param charset The character set to use to communicate with the managed process.
   */
  public SimpleProcessManager(ProcessBuilder builder, Charset charset) {
    this(builder, charset, (Long) null, null, null);
  }

  @Override
  public boolean startsUpInstantly() {
    return startsUpInstantly;
  }

  @Override
  public boolean isProcessStartedUp(String outputLine, boolean error) throws FailedStartupException {
    return error ?
        startupPredicateStdErr == null || startupPredicateStdErr.isStartedUp(outputLine, getStartupOutputStore()) :
        startupPredicateStdOut == null || startupPredicateStdOut.isStartedUp(outputLine, getStartupOutputStore());
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
  public interface ProcessStartupPredicate {

    /**
     * Returns whether the process is started up and ready to execute commands based on the latest line of output.
     *
     * @param outputLine The latest line output by the process.
     * @param startupOutputStore The output store holding all previous pre-startup outputs of the process.
     * @return Whether the latest line of output signals the startup of the process.
     * @throws FailedStartupException If the output denotes process failure.
     */
    boolean isStartedUp(String outputLine, ProcessOutputStore startupOutputStore) throws FailedStartupException;

  }

}