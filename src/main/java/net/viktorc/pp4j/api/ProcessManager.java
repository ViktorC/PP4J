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
package net.viktorc.pp4j.api;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Optional;

/**
 * An interface that defines methods that allow for the managing of the life cycle of a process. It defines methods that start the process,
 * determine and handle its startup behavior, and allow for its orderly termination. The same instance may be used for the management of
 * multiple subsequent processes, thus the implementation should allow for reusability.
 *
 * @author Viktor Csomor
 */
public interface ProcessManager {

  /**
   * A method that starts a new process. The process created should always be the same and it should always be started only upon the call of
   * this method.
   *
   * @return A new process.
   * @throws IOException If the process cannot be started.
   */
  Process start() throws IOException;

  /**
   * Returns the character set to use to communicate with the managed process through its standard streams.
   *
   * @return The character set to use when reading from and writing to the process' streams.
   */
  Charset getEncoding();

  /**
   * A method that denotes whether the process should be considered started up instantly or if it is only started up once a certain output
   * has been printed to its standard out or standard error streams. If it returns <code>true</code>, the  process is instantly considered
   * started up and ready as soon as it is running, and the method {@link #onStartup()} is executed. If it returns <code>false</code>,
   * the method {@link #isStartedUp(String, boolean)} determines when the process is considered started up.
   *
   * @return Whether the process instantly start up as soon as it is run or if it is started up and ready only when a certain output has
   * been written to one of its output streams.
   */
  boolean startsUpInstantly();

  /**
   * Handles the output of the underlying process after it has been started. The return value of the method determines whether the process
   * is to be considered started up and ready for the execution of the method {@link #onStartup()}. It is only ever called if
   * {@link #startsUpInstantly()} returns <code>false</code>.
   *
   * @param outputLine A line of output produced by the process.
   * @param error Whether this line has been output to the standard error or the standard out stream.
   * @return Whether the process is to be considered started up.
   * @throws FailedStartupException If the output signals a process failure and the process is not expected to recover.
   */
  boolean isStartedUp(String outputLine, boolean error) throws FailedStartupException;

  /**
   * Determines the duration of continuous idleness after which the process is to be terminated. The process is considered idle if it is
   * started up and not processing a submission.
   *
   * @return The number of milliseconds of idleness after which the process is to be terminated.
   */
  Optional<Long> getKeepAliveTime();

  /**
   * Returns an optional submission to execute upon starting up the process. No other submissions are to be accepted until this submission
   * is processed. If the returned optional is empty, no initial submission is executed.
   *
   * @return The optional initial submission that is for setting up the process for later submissions.
   */
  Optional<Submission<?>> getInitSubmission();

  /**
   * Returns an optional submission for terminating the process.  It allows for an opportunity to execute commands to close resources or
   * to exit the process in an orderly way. If the returned optional is empty, the process is killed forcibly.
   *
   * @return The optional termination submission for shutting down the process.
   */
  Optional<Submission<?>> getTerminationSubmission();

  /**
   * A callback method invoked after the process is started up and the initial submission, if there is one, is executed.
   */
  default void onStartup() {
  }

  /**
   * A callback method invoked after the process terminates. Its main purpose is to allow for wrap-up activities.
   *
   * @param returnCode The return code of the terminated process.
   */
  default void onTermination(int returnCode) {
  }

}