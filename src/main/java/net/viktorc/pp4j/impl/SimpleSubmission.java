package net.viktorc.pp4j.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import net.viktorc.pp4j.api.Command;
import net.viktorc.pp4j.api.Submission;

/**
 * A simple implementation of the {@link net.viktorc.pp4j.api.Submission} interface that allows for the 
 * specification of the commands to execute and whether the process is to be terminated after the execution of 
 * the commands.
 * 
 * @author Viktor Csomor
 *
 */
public class SimpleSubmission implements Submission<Object> {

	private final List<Command> commands;
	
	/**
	 * Constructs an instance according to the specified parameters.
	 * 
	 * @param commands A list of commands to execute. It should not contain null references.
	 * @throws IllegalArgumentException If the commands are null or empty or contain at least one null reference.
	 */
	public SimpleSubmission(List<Command> commands) {
		if (commands == null)
			throw new IllegalArgumentException("The commands cannot be null.");
		if (commands.isEmpty())
			throw new IllegalArgumentException("The commands cannot be empty.");
		if (!commands.stream().filter(c -> c == null).collect(Collectors.toList()).isEmpty())
			throw new IllegalArgumentException("The commands cannot include null references.");
		this.commands = new ArrayList<>(commands);
	}
	/**
	 * Constructs an instance according to the specified parameters.
	 * 
	 * @param command A command to execute.
	 * @throws IllegalArgumentException If the command is null.
	 */
	public SimpleSubmission(Command command) {
		this(Arrays.asList(command));
	}
	@Override
	public List<Command> getCommands() {
		return new ArrayList<>(commands);
	}
	@Override
	public String toString() {
		return String.format("{commands:[%s]}", String.join(",", commands.stream()
				.map(c -> "\"" + c.getInstruction() + "\"").collect(Collectors.toList())));
	}

}