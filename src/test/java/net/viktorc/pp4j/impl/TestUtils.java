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

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.function.Supplier;
import net.viktorc.pp4j.api.FailedStartupException;
import net.viktorc.pp4j.api.ProcessManager;
import net.viktorc.pp4j.api.ProcessManagerFactory;
import net.viktorc.pp4j.api.Submission;

/**
 * A utility class for testing.
 *
 * @author Viktor Csomor
 */
class TestUtils {

  private static final String BASE_PATH = "net/viktorc/pp4j/impl/";
  private static final String EXECUTABLE_PATH;
  private static final String LIBRARY_PATH;
  static {
    String osName = System.getProperty("os.name").toLowerCase();
    if ("mac os x".equals(osName)) {
      EXECUTABLE_PATH = BASE_PATH + "osx/testwrapper";
      LIBRARY_PATH = BASE_PATH + "osx/libtest.so";
    } else if (osName.contains("win")) {
      EXECUTABLE_PATH = BASE_PATH + "win/testwrapper.exe";
      LIBRARY_PATH = BASE_PATH + "win/test.dll";
    } else {
      EXECUTABLE_PATH = BASE_PATH + "linux/testwrapper";
      LIBRARY_PATH = BASE_PATH + "linux/libtest.so";
    }
  }

  private TestUtils() { }

  /**
   * It returns a file resource given the file path.
   *
   * @param path The file path.
   * @return The file or <code>null</code> if the path is invalid.
   */
  private static File getFileResource(String path) {
    try {
      URL resourceUrl = ClassLoader.getSystemClassLoader().getResource(path);
      if (resourceUrl != null) {
        URI resourceUri = resourceUrl.toURI();
        File file = new File(resourceUri.getPath());
        if (file.setExecutable(true)) {
          return file;
        }
      }
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
    return null;
  }

  /**
   * Returns a {@link File} instance representing the test executable.
   *
   * @return A <code>File</code> pointing to the test executable.
   */
  static File getExecutable() {
    return getFileResource(EXECUTABLE_PATH);
  }

  /**
   * Returns a {@link File} instance representing the test library.
   *
   * @return A <code>File</code> pointing to the test library.
   */
  static File getLibrary() {
    return getFileResource(LIBRARY_PATH);
  }

  /**
   * An implementation of the {@link ProcessManagerFactory} interface for testing purposes.
   *
   * @author Viktor Csomor
   */
  static class TestProcessManagerFactory implements ProcessManagerFactory {

    private final Long keepAliveTime;
    private final boolean verifyStartup;
    private final boolean throwStartupException;
    private final ProcessBuilder builder;
    private final Charset charset;
    private final Supplier<Submission<?>> initSubmissionSupplier;
    private final Supplier<Submission<?>> terminationSubmissionSupplier;

    /**
     * Constructs an instance according to the specified parameters.
     *
     * @param keepAliveTime The maximum allowed duration of idleness before the process is terminated.
     * @param verifyStartup Whether the startup should be verified.
     * @param executeInitSubmission Whether an initial submission is to be executed upon the startup of the process.
     * @param manuallyTerminate Whether the process should be terminated in an orderly way or forcibly.
     * @param throwStartupException Whether a process exception should be thrown on startup.
     */
    TestProcessManagerFactory(Long keepAliveTime, boolean verifyStartup, boolean executeInitSubmission, boolean manuallyTerminate,
        boolean throwStartupException) {
      this.keepAliveTime = keepAliveTime;
      this.verifyStartup = verifyStartup;
      this.throwStartupException = throwStartupException;
      builder = new ProcessBuilder(getExecutable().getAbsolutePath());
      charset = Charset.defaultCharset();
      initSubmissionSupplier = () -> executeInitSubmission ?
          new SimpleSubmission<>(new SimpleCommand("start", (o, s) -> "ok".equals(o))) :
          null;
      terminationSubmissionSupplier = () -> manuallyTerminate ?
          new SimpleSubmission<>(new SimpleCommand("stop", (o, s) -> "bye".equals(o))) :
          null;
    }

    /**
     * Constructs a default test process manager factory.
     */
    TestProcessManagerFactory() {
      this(null, true, false, false, false);
    }

    @Override
    public ProcessManager newProcessManager() {
      if (verifyStartup) {
        return new SimpleProcessManager(builder,
            charset,
            throwStartupException ? (outputLine, outputStore) -> {
              throw new FailedStartupException("test");
            } : (outputLine, outputStore) -> "hi".equals(outputLine),
            keepAliveTime,
            initSubmissionSupplier,
            terminationSubmissionSupplier);
      }
      return new SimpleProcessManager(builder, charset, keepAliveTime, initSubmissionSupplier, terminationSubmissionSupplier);
    }

  }

}
