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
import java.util.stream.Collectors;
import net.viktorc.pp4j.api.Submission;

/**
 * An abstract implementation of the {@link Submission} interface that allows for storing the result of the execution of the submission.
 *
 * @param <T> The expected type of the result of the submission.
 * @author Viktor Csomor
 */
public abstract class AbstractSubmission<T> implements Submission<T> {

  private volatile T result;

  /**
   * Sets the optional result of the submission.
   *
   * @param result The result of the submission which may be <code>null</code>.
   */
  protected void setResult(T result) {
    this.result = result;
  }

  @Override
  public Optional<T> getResult() {
    return Optional.ofNullable(result);
  }

  @Override
  public String toString() {
    return String.format("{commands:[%s]}", getCommands().stream()
        .map(c -> "\"" + c.getInstruction() + "\"")
        .collect(Collectors.joining(",")));
  }

}
