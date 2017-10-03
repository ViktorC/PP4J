/*
 * Copyright 2017 Viktor Csomor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.viktorc.pp4j.api;

/**
 * An interface that defines an executor that encapsulates a process and serves as a handle for having the process 
 * execute submissions.
 * 
 * @author Viktor Csomor
 *
 */
public interface ProcessExecutor {

	/**
	 * Sequentially writes the specified commands to the process and blocks until they are processed. The result of 
	 * the submission, if there is one, can be subsequently  accessed by calling the 
	 * {@link net.viktorc.pp4j.api.Submission#getResult()} method.
	 * 
	 * @param submission The submission to execute.
	 * @return Whether the submission was successfully executed. If the executor is not running or is busy 
	 * processing an other submission, it immediately returns false; otherwise the submission is executed and 
	 * true is returned once it's successfully processed, or false is returned if the submission could not be 
	 * processed.
	 * @throws Exception If an unexpected error occurs.
	 */
	boolean execute(Submission<?> submission) throws Exception;

}