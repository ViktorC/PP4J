package net.viktorc.pp4j.impl.jp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.Callable;

class JavaProcess {

	static final String COMPLETION_SIGNAL = "--ok--";
	
	public static void main(String[] args) throws Exception {
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
			System.err.println(ConversionUtil.encode(e));
		}
	}

}
