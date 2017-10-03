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
public class SPETest {

	@Rule
	public final ExpectedException exceptionRule = ExpectedException.none();
	
	@Test
	public void test01() throws Exception {
		StandardProcessExecutor executor = new StandardProcessExecutor(
				TestUtils.createTestProcessManagerFactory().newProcessManager());
		SimpleCommand command = new SimpleCommand("process 3",
				(c, o) -> "ready".equals(o), (c, o) -> false);
		executor.start();
		executor.execute(new SimpleSubmission(command));
		Assert.assertTrue("in progress\nin progress\nready"
				.equals(command.getJointStandardOutLines()));
	}
	@Test
	public void test02() throws Exception {
		StandardProcessExecutor executor = new StandardProcessExecutor(
				TestUtils.createTestProcessManagerFactory().newProcessManager());
		executor.start();
		exceptionRule.expect(IllegalStateException.class);
		executor.start();
	}
	@Test
	public void test03() throws Exception {
		StandardProcessExecutor executor = new StandardProcessExecutor(
				TestUtils.createTestProcessManagerFactory().newProcessManager());
		executor.start();
		executor.stop(true);
		Thread.sleep(500);
		exceptionRule.expect(IllegalStateException.class);
		executor.start();
	}
	
}
