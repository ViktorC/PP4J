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
import org.junit.Assert;
import org.junit.Test;

/**
 * A unit test class for {@link JavaObjectCodec}.
 *
 * @author Viktor Csomor
 */
public class JavaObjectCodecTest extends TestCase {

  @Test
  public void testEncodeThrowsIOExceptionIfObjectNotSerializable() throws IOException {
    exceptionRule.expect(IOException.class);
    Object object = new Object();
    JavaObjectCodec.getInstance().encode(object);
  }

  @Test
  public void testDecodeThrowsIllegalArgumentExceptionIfNotBase64() throws IOException, ClassNotFoundException {
    exceptionRule.expect(IllegalArgumentException.class);
    JavaObjectCodec.getInstance().decode("This string is not Base64");
  }

  @Test
  public void testDecodeThrowsIOExceptionIfNotSerializedObject() throws IOException, ClassNotFoundException {
    exceptionRule.expect(IOException.class);
    JavaObjectCodec.getInstance().decode("VGhpcyBpcyBub3QgYSBzZXJpYWxpemVkIG9iamVjdA==");
  }

  @Test
  public void testEncodeDecode() throws IOException, ClassNotFoundException {
    Integer integer = 1;
    String encodedInteger = JavaObjectCodec.getInstance().encode(integer);
    Object decodedObject = JavaObjectCodec.getInstance().decode(encodedInteger);
    Assert.assertTrue(decodedObject instanceof Integer);
    Integer decodedEncodedInteger = (Integer) decodedObject;
    Assert.assertEquals(integer, decodedEncodedInteger);
    Assert.assertNotSame(integer, decodedEncodedInteger);
  }

}
