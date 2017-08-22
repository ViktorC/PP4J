package net.viktorc.pp4j.impl.jpp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
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
	 * A string for signaling the startup of the Java process; it contains characters that cannot 
	 * ever appear in Base64 encoded messages.
	 */
	static final String STARTUP_SIGNAL = "--READY--";
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
			PrintStream out = System.out;
			PrintStream err = System.err;
			warmUp();
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
							System.setOut(dummyOut);
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
	 * A task for warming up the JVM instance; it ensures that some of the most important and most 
	 * commonly used classes are loaded.
	 */
	private static void warmUp() {
		try {
			Callable<Integer> task = (Callable<Integer> & Serializable) () -> {
				Set<Object> set = new HashSet<>();
				set.add(true);
				set.add('C');
				set.add((byte) 16);
				set.add((short) 1024);
				set.add(100000);
				set.add(0xe8fc763dL);
				set.add((float) 0.5);
				set.add(2.718281);
				set.add("string");
				set.add(Thread.currentThread());
				set.add(Executors.callable(() -> { }, new AtomicInteger()));
				List<?> linkedList = new LinkedList<>(set);
				Map<Object,Boolean> map = new HashMap<>();
				Random rand = new Random();
				linkedList.stream().forEach(e -> map.put(e, rand.nextDouble() >= 0.5));
				int[] nums = new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 };
				List<?> list = Arrays.asList(nums);
				List<?> arrayList = new ArrayList<>(list);
				arrayList.clear();
				return (int) Arrays.stream(nums).filter(n -> n%2 != 0).average().getAsDouble();
			};
			String encodedTask = ConversionUtil.encode(task);
			Object decodedTask = ConversionUtil.decode(encodedTask);
			((Callable<?>) decodedTask).call();
		} catch (Exception e) {
			return;
		} finally {
			System.gc();
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
