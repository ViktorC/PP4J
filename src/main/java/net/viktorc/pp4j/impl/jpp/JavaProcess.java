package net.viktorc.pp4j.impl.jpp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
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
	 * The method executed as a separate process. It listens to its standard in for 
	 * encoded and serialized {@link java.util.concurrent.Callable} instances, which it 
	 * decodes, deserializes, and executes on receipt. The return value is normally output 
	 * to its stdout stream in the form of the serialized and encoded return values of the 
	 * <code>Callable</code> instances. If an exception occurs, it is serialized, encoded, 
	 * and output to the stderr stream.
	 * 
	 * @param args They are ignored for now.
	 */
	public static void main(String[] args) {
		try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
				DummyPrintStream dummyOut = new DummyPrintStream();
				DummyPrintStream dummyErr = new DummyPrintStream()) {
			boolean stop = false;
			PrintStream out = System.out;
			PrintStream err = System.err;
			while (!stop) {
				try {
					String line = in.readLine().trim();
					if (line.isEmpty())
						continue;
					Object input = ConversionUtil.decode(line);
					if (input instanceof Callable<?>) {
						Callable<?> c = (Callable<?>) input;
						/* Try to redirect the out streams to make sure that print 
						 * statements and such do not cause the submission to be assumed 
						 * done falsely. */
						try {
							System.setOut(dummyOut);
							System.setErr(dummyErr);
						} catch (SecurityException e) {
							// Ignore it.
						}
						Object output = c.call();
						try {
							System.setOut(out);
							System.setErr(err);
						} catch (SecurityException e) {
							// Ignore it.
						}
						System.out.println(ConversionUtil.encode(output));
					} else
						continue;
				} catch (Throwable e) {
					System.err.println(ConversionUtil.encode(e));
				}
			}
		} catch (Throwable e) {
			try {
				System.err.println(ConversionUtil.encode(e));
			} catch (Exception e1) {
				System.err.println(e.getMessage());
			}
		}
	}
	
	/**
	 * A dummy implementation of {@link java.io.PrintStream} that does nothing on <code>
	 * write</code>.
	 * 
	 * @author Viktor Csomor
	 *
	 */
	private static class DummyPrintStream extends PrintStream {

		/**
		 * Default constructor.
		 */
		DummyPrintStream() {
			super(new OutputStream() {

				@Override
				public void write(int b) throws IOException {
					// This is why it is called "DummyPrintStream".
				}
			});
		}
		
	}

}
