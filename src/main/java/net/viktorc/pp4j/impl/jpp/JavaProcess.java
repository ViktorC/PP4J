package net.viktorc.pp4j.impl.jpp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The class whose main method is run as a separate process by the Java process based 
 * process pool executor.
 * 
 * @author Viktor Csomor
 *
 */
class JavaProcess {
	
	/**
	 * A string for signaling the startup of the Java process; it contains characters that 
	 * cannot ever appear in Base64 encoded messages.
	 */
	static final String STARTUP_SIGNAL = "--READY--";
	private static final Callable<?> WARMUP_TASK = (Callable<?> & Serializable) () -> {
		Callable<AtomicInteger> task = (Callable<AtomicInteger> & Serializable) () -> {
			AtomicInteger counter = new AtomicInteger();
			for (int i = 0; i < 1000; i++)
				counter.incrementAndGet();
			return counter;
		};
		String encodedTask = ConversionUtil.encode(task);
		Object decodedTask = ConversionUtil.decode(encodedTask);
		((Callable<?>) decodedTask).call();
		return STARTUP_SIGNAL;
	};
	
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
			/* Make sure that as many of the relevant classes are loaded as possible 
			 * and signal the startup of the process. */
			System.out.println(WARMUP_TASK.call());
			try {
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
							} catch (SecurityException e) { /* Ignore it. */ }
							Object output = c.call();
							try {
								System.setOut(out);
							} catch (SecurityException e) { /* Ignore it. */ }
							System.out.println(ConversionUtil.encode(output));
						} else
							continue;
					} catch (Throwable e) {
						try {
							System.setErr(err);
						} catch (SecurityException e1) { /* Ignore it. */ }
						System.err.println(ConversionUtil.encode(e));
					}
				}
			} catch (Throwable e) {
				try {
					System.setErr(err);
				} catch (SecurityException e1) { /* Ignore it. */ }
				throw e;
			}
		} catch (Throwable e) {
			try {
				System.err.println(ConversionUtil.encode(e));
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
