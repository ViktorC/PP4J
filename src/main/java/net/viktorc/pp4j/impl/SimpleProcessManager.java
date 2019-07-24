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

import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import net.viktorc.pp4j.api.Submission;

/**
 * A simple sub-class of the {@link net.viktorc.pp4j.impl.AbstractProcessManager} abstract class. It assumes that the process is immediately
 * started up as soon as it is launched (without having to wait for a certain output denoting that the process is ready), it has the process
 * forcibly killed every time it needs to be terminated due to timing out or not being reusable, and it implements no callback for when the
 * process terminates.
 *
 * @author Viktor Csomor
 */
public class SimpleProcessManager extends AbstractProcessManager {

  private final BiPredicate<String, Boolean> startupPredicate;
  private final Supplier<Submission<?>> initSubmissionSupplier;
  private final Supplier<Submission<?>> terminationSubmissionSupplier;

  /**
   * Constructs a manager for the processes created by the specified {@link ProcessBuilder}.
   *
   * @param builder The instance to build the processes with.
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
  public SimpleProcessManager(ProcessBuilder builder, Long keepAliveTime, BiPredicate<String, Boolean> startupPredicate,
      Supplier<Submission<?>> initSubmissionSupplier, Supplier<Submission<?>> terminationSubmissionSupplier) {
    super(builder, keepAliveTime);
    this.startupPredicate = startupPredicate;
    this.initSubmissionSupplier = initSubmissionSupplier;
    this.terminationSubmissionSupplier = terminationSubmissionSupplier;
  }

  /**
   * Constructs a manager for the processes created by the specified {@link ProcessBuilder}.
   *
   * @param builder The instance to build the processes with.
   * @param keepAliveTime The number of milliseconds after which idle processes are terminated. If it is <code>null</code>, the life span
   * of the process will not be limited.
   * @param startupPredicate The predicate to use to determine when the process can be considered started up. The two parameters are the
   * line output to the process' stream and whether that stream is the standard error or the standard out stream. If it is
   * <code>null</code>, the process is assumed to start up instantaneously.
   */
  public SimpleProcessManager(ProcessBuilder builder, Long keepAliveTime, BiPredicate<String, Boolean> startupPredicate) {
    this(builder, keepAliveTime, startupPredicate, null, null);
  }

  /**
   * Constructs a manager for the processes created by the specified {@link ProcessBuilder}.
   *
   * @param builder The instance to build the processes with.
   * @param keepAliveTime The number of milliseconds after which idle processes are terminated. If it is <code>null</code>, the life span
   * of the process will not be limited.
   */
  public SimpleProcessManager(ProcessBuilder builder, Long keepAliveTime) {
    this(builder, keepAliveTime, null, null, null);
  }

  /**
   * Constructs a manager for the processes created by the specified {@link ProcessBuilder}.
   *
   * @param builder The instance to build the processes with.
   */
  public SimpleProcessManager(ProcessBuilder builder) {
    this(builder, null, null, null, null);
  }

  @Override
  public boolean startsUpInstantly() {
    return startupPredicate == null;
  }

  @Override
  public boolean isStartedUp(String outputLine, boolean error) {
    return startupPredicate == null || startupPredicate.test(outputLine, error);
  }

  @Override
  public Optional<Submission<?>> getInitSubmission() {
    return Optional.ofNullable(initSubmissionSupplier == null ? null : initSubmissionSupplier.get());
  }

  @Override
  public Optional<Submission<?>> getTerminationSubmission() {
    return Optional.ofNullable(terminationSubmissionSupplier == null ? null : terminationSubmissionSupplier.get());
  }

}