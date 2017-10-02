package net.viktorc.pp4j.impl;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import net.viktorc.pp4j.impl.SimpleCommand;
import net.viktorc.pp4j.impl.SimpleSubmission;
import net.viktorc.pp4j.impl.StandardProcessExecutor;

/**
 * Test cases for the {@link net.viktorc.pp4j.impl.StandardProcessExecutor}.
 * 
 * @author Viktor
 *
 */
public class StandardProcessExecutorTest {

	@Rule
	public final ExpectedException exceptionRule = ExpectedException.none();
	
	@Test
	public void test01() throws Exception {
		try (StandardProcessExecutor executor = new StandardProcessExecutor(
				TestUtils.createTestProcessManagerFactory().newProcessManager())) {
			executor.start();
			SimpleCommand command = new SimpleCommand("process 3",
					(c, o) -> "ready".equals(o), (c, o) -> false);
			executor.execute(new SimpleSubmission(command));
			Assert.assertTrue("in progress\nin progress\nready"
					.equals(command.getJointStandardOutLines()));
		}
	}
	@Test
	public void test02() throws Exception {
		try (StandardProcessExecutor executor = new StandardProcessExecutor(
				TestUtils.createTestProcessManagerFactory().newProcessManager())) {
			executor.start();
			exceptionRule.expect(IllegalStateException.class);
			executor.start();
		}
	}
	
}
