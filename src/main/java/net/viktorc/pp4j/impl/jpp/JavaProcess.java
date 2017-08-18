package net.viktorc.pp4j.impl.jpp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.Callable;

/**
 * The class whose main method is run as a separate process by the Java process based 
 * process pool executor.
 * 
 * @author Viktor Csomor
 *
 */
class JavaProcess {

	/**
	 * Ensure that it contains characters that cannot be found in a Base64 encoded string.
	 */
	static final String COMPLETION_SIGNAL = "--ok--";
	
	/**
	 * The method executed as a separate process. It listens to its standard in for 
	 * encoded and serialized {@link java.lang.Runnable} and {@link java.util.concurrent.Callable} 
	 * instances, which it decodes, deserializes, and executes on receipt. The return value 
	 * is normally output to its stdout stream. In case of <code>Callable</code> instances, 
	 * it is the serialized and encoded return values; while in case of <code>Runnable</code> 
	 * instances, it is a simply string denoting the successful completion of execution. If 
	 * an exception occurs, it is serialized, encoded, and output to the stderr stream.
	 * 
	 * @param args They are ignored for now.
	 */
	public static void main(String[] args) {
		try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
			boolean stop = false;
			while (!stop) {
				try {
					String line = in.readLine().trim();
					if (line.isEmpty())
						continue;
					Object input = ConversionUtil.decode(line);
					if (input instanceof Runnable) {
						Runnable r = (Runnable) input;
						r.run();
						System.out.println(COMPLETION_SIGNAL);
					} else if (input instanceof Callable<?>) {
						Callable<?> c = (Callable<?>) input;
						System.out.println(ConversionUtil.encode(c.call()));
					} else
						continue;
				} catch (Throwable e) {
					System.err.println(ConversionUtil.encode(e));
				}
			}
		} catch (Throwable e) {
			try {
				System.err.println(ConversionUtil.encode(e));
			} catch (IOException e1) {
				System.err.println(e.getMessage());
			}
		}
	}

}
