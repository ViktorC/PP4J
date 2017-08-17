package net.viktorc.pp4j.impl.jp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Base64;

class ConversionUtil {

	private ConversionUtil() { };
	static String encode(Object o) throws IOException {
		try (ByteArrayOutputStream byteArrayOut = new ByteArrayOutputStream();
				ObjectOutputStream objectOutput = new ObjectOutputStream(byteArrayOut)) {
			objectOutput.writeObject(o);
			return Base64.getEncoder().encodeToString(byteArrayOut.toByteArray());
		}
	}
	static Object decode(String s) throws IOException, ClassNotFoundException {
		byte[] bytes = Base64.getDecoder().decode(s);
		try (ObjectInputStream objectInput = new ObjectInputStream(
				new ByteArrayInputStream(bytes))) {
			return objectInput.readObject();
		}
	}
}
