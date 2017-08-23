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
	 * A string for signaling the startup of the Java process; it contains characters that cannot 
	 * ever appear in Base64 encoded messages.
	 */
	static final String STARTUP_SIGNAL = "--READY--";
	/**
	 * A prefix denoting that the response contains a serialized <code>Throwable<code> instance 
	 * that was thrown during the execution of the task.
	 */
	static final String ERROR_PREFIX = "--ERROR--";
	/**
	 * The request for the termination of the program.
	 */
	static final String STOP_REQUEST = "--STOP--";
	/**
	 * The response to a termination request.
	 */
	static final String STOP_SIGNAL = "--STOPPED--";
	
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
			/* As the process' stderr is redirected to the stdout, there is no need for a 
			 * reference to this stream; neither should the submissions be able to print to 
			 * stdout through the System.err stream. */
			try {
				System.setErr(dummyErr);
			} catch (SecurityException e) { /* Ignore it. */ }
			PrintStream out = System.out;
			// Send the startup signal.
			System.out.println(STARTUP_SIGNAL);
			try {
				while (!stop) {
					try {
						String line = in.readLine().trim();
						if (line.isEmpty())
							continue;
						if (STOP_REQUEST.equals(line)) {
							System.out.println(STOP_SIGNAL);
							return;
						}
						Object input = Conversion.toObject(line);
						if (input instanceof Callable<?>) {
							Callable<?> c = (Callable<?>) input;
							/* Try to redirect the out stream to make sure that print 
							 * statements and such do not cause the submission to be assumed 
							 * done falsely. */
							try {
								System.setOut(dummyOut);
							} catch (SecurityException e) { /* Ignore it. */ }
							Object output = c.call();
							try {
								System.setOut(out);
							} catch (SecurityException e) { /* Ignore it. */ }
							System.out.println(Conversion.toString(output));
						} else
							continue;
					} catch (Throwable e) {
						try {
							System.setOut(out);
						} catch (SecurityException e1) { /* Ignore it. */ }
						System.out.println(ERROR_PREFIX + Conversion.toString(e));
					}
				}
			} catch (Throwable e) {
				try {
					System.setOut(out);
				} catch (SecurityException e1) { /* Ignore it. */ }
				throw e;
			}
		} catch (Throwable e) {
			try {
				System.out.println(ERROR_PREFIX + Conversion.toString(e));
			} catch (Exception e1) { /* Give up all hope. */ }
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
