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

/**
 * An interface that defines an executor that encapsulates a process and serves as a handle for having the process execute submissions.
 *
 * @author Viktor Csomor
 */
public interface ProcessExecutor {

  /**
   * It has the process sequentially execute the commands contained in the submission and blocks until the execution of every command is
   * complete. The result of the submission, if there is one, can be subsequently accessed by calling the
   * {@link Submission#getResult()} method.
   *
   * @param submission The submission to execute.
   * @throws FailedCommandException If one of the submission's commands fails.
   * @throws DisruptedExecutionException If the executor is stopped before it could complete the execution of the submission, or if there
   * is any other error that is not caused by the submission that leaves the execution of the submission incomplete. Implementations of
   * the <code>ProcessExecutor</code> are encouraged to terminate the executor when throwing this exception.
   */
  void execute(Submission<?> submission) throws FailedCommandException, DisruptedExecutionException;

}