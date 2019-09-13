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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;

/**
 * A singleton for encoding Java objects as strings and decoding strings into Java objects. It uses the default {@link ObjectInputStream}
 * and {@link ObjectOutputStream} classes for the serialization and deserialization of Java objects to and from byte array streams, and it
 * uses Base64 for the encoding and decoding of the byte array streams.
 *
 * @author Viktor Csomor
 */
public class JavaObjectCodec {

  /**
   * The character set used for encoding and decoding.
   */
  public static final Charset CHARSET = StandardCharsets.ISO_8859_1;

  private static final JavaObjectCodec INSTANCE = new JavaObjectCodec();

  private final Encoder base64Encoder;
  private final Decoder base64Decoder;

  /**
   * Returns the only <code>JavaObjectCodec</code> instance.
   *
   * @return A reference to the one and only <code>JavaObjectCodec</code> instance.
   */
  public static JavaObjectCodec getInstance() {
    return INSTANCE;
  }

  /**
   * Initializes the encoder and decoder references.
   */
  private JavaObjectCodec() {
    base64Encoder = Base64.getEncoder();
    base64Decoder = Base64.getDecoder();
  }

  /**
   * Serializes the specified object into a string and encodes it using Base64.
   *
   * @param object The object to serialize and encode.
   * @return The serialized and encoded object as a string.
   * @throws IOException If the serialization fails.
   */
  public String encode(Object object) throws IOException {
    try (ByteArrayOutputStream byteArrayOut = new ByteArrayOutputStream();
        ObjectOutputStream objectOutput = new ObjectOutputStream(byteArrayOut)) {
      objectOutput.writeObject(object);
      return new String(base64Encoder.encode(byteArrayOut.toByteArray()), CHARSET);
    }
  }

  /**
   * Decodes the string and deserializes it into an object.
   *
   * @param string The Base64-encoded string to deserialize.
   * @return The decoded and deserialized string as an object.
   * @throws IOException If the deserialization fails.
   * @throws ClassNotFoundException If the deserialization fails due to the class of the object not having been found.
   * @throws IllegalArgumentException If the string is not a valid Base64 string.
   */
  public Object decode(String string) throws IOException, ClassNotFoundException {
    byte[] bytes = base64Decoder.decode(string.getBytes(CHARSET));
    try (ObjectInputStream objectInput = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
      return objectInput.readObject();
    }
  }

}
