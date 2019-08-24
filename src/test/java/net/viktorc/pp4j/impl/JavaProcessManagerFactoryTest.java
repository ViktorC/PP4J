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
import org.junit.Test;

/**
 * A unit test class for {@link JavaProcessManagerFactory}.
 *
 * @author Viktor Csomor
 */
public class JavaProcessManagerFactoryTest extends TestCase {

  @Test
  public void testThrowsIllegalArgumentExceptionIfConfigNull() {
    exceptionRule.expect(IllegalArgumentException.class);
    new JavaProcessManagerFactory<>(null);
  }

  @Test
  public void testThrowsIllegalArgumentExceptionIfJavaLauncherCommandNull() {
    exceptionRule.expect(IllegalArgumentException.class);
    new JavaProcessManagerFactory<>(new SimpleJavaProcessConfig(null));
  }

  @Test
  public void testThrowsIllegalArgumentExceptionIfJavaLauncherCommandEmpty() {
    exceptionRule.expect(IllegalArgumentException.class);
    new JavaProcessManagerFactory<>(new SimpleJavaProcessConfig(""));
  }

  @Test
  public void testThrowsIllegalArgumentExceptionIfInitHeapSizeZero() {
    exceptionRule.expect(IllegalArgumentException.class);
    new JavaProcessManagerFactory<>(new SimpleJavaProcessConfig(0, 1, 1));
  }

  @Test
  public void testThrowsIllegalArgumentExceptionIfInitHeapSizeLessThanZero() {
    exceptionRule.expect(IllegalArgumentException.class);
    new JavaProcessManagerFactory<>(new SimpleJavaProcessConfig(-1, 1, 1));
  }

  @Test
  public void testThrowsIllegalArgumentExceptionIfMaxHeapSizeZero() {
    exceptionRule.expect(IllegalArgumentException.class);
    new JavaProcessManagerFactory<>(new SimpleJavaProcessConfig(1, 0, 1));
  }

  @Test
  public void testThrowsIllegalArgumentExceptionIfMaxHeapSizeLessThanZero() {
    exceptionRule.expect(IllegalArgumentException.class);
    new JavaProcessManagerFactory<>(new SimpleJavaProcessConfig(1, -1, 1));
  }

  @Test
  public void testThrowsIllegalArgumentExceptionIfStackSizeZero() {
    exceptionRule.expect(IllegalArgumentException.class);
    new JavaProcessManagerFactory<>(new SimpleJavaProcessConfig(1, 1, 0));
  }

  @Test
  public void testThrowsIllegalArgumentExceptionIfStackSizeLessThanZero() {
    exceptionRule.expect(IllegalArgumentException.class);
    new JavaProcessManagerFactory<>(new SimpleJavaProcessConfig(1, 1, -1));
  }

  @Test
  public void testThrowsIllegalArgumentExceptionIfClassPathEmpty() {
    exceptionRule.expect(IllegalArgumentException.class);
    new JavaProcessManagerFactory<>(new SimpleJavaProcessConfig() {

      @Override
      public Optional<String> getClassPath() {
        return Optional.of("");
      }
    });
  }

}
