package net.viktorc.pp4j.impl;

import java.io.File;
import java.net.URISyntaxException;

import net.viktorc.pp4j.api.ProcessManager;
import net.viktorc.pp4j.api.ProcessManagerFactory;
import net.viktorc.pp4j.impl.SimpleProcessManager;

/**
 * A utility class for testing.
 * 
 * @author Viktor Csomor
 *
 */
public class TestUtils {

	/**
	 * Not initializable.
	 */
	private TestUtils() { }
	/**
	 * Returns a {@link java.io.File} instance representing the test executable.
	 * 
	 * @return A <code>File</code> pointing to the test executable.
	 * @throws URISyntaxException If the path to the executable cannot be resolved.
	 */
	public static File getExecutable() throws URISyntaxException {
		// Support testing both on Linux and Windows.
		boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
		return new File(ClassLoader.getSystemClassLoader()
				.getResource(windows ? "win/test.exe" : "linux/test")
				.toURI().getPath());
	}
	/**
	 * Returns a test {@link net.viktorc.pp4j.api.ProcessManagerFactory} instance.
	 * 
	 * @return A test <code>ProcessManagerFactory</code> instance.
	 * @throws URISyntaxException If the path to the executable cannot be resolved.
	 */
	public static ProcessManagerFactory createTestProcessManagerFactory()
			throws URISyntaxException {
		return new TestProcessManagerFactory();
	}
	
	/**
	 * A simple test process manager factory for starting process managers for the test program.
	 * 
	 * @author Viktor Csomor
	 *
	 */
	private static class TestProcessManagerFactory implements ProcessManagerFactory {

		ProcessBuilder builder;
		
		/**
		 * Constructs an instance for creating process managers.
		 * 
		 * @throws URISyntaxException If the path to the test executable cannot be resolved.
		 */
		TestProcessManagerFactory() throws URISyntaxException {
			File programFile = getExecutable();
			programFile.setExecutable(true);
			builder = new ProcessBuilder(programFile.getAbsolutePath());
		}
		@Override
		public ProcessManager newProcessManager() {
			return new SimpleProcessManager(builder);
		}
		
	}
	
}
