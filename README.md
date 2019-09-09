# PP4J [![Build Status](https://travis-ci.org/ViktorC/PP4J.svg?branch=master)](https://travis-ci.org/ViktorC/PP4J) [![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=net.viktorc:pp4j&metric=alert_status)](https://sonarcloud.io/dashboard?id=net.viktorc:pp4j) [![SonarCloud Coverage](https://sonarcloud.io/api/project_badges/measure?project=net.viktorc:pp4j&metric=coverage)](https://sonarcloud.io/dashboard?id=net.viktorc:pp4j) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/net.viktorc/pp4j/badge.svg?style=plastic)](https://maven-badges.herokuapp.com/maven-central/net.viktorc/pp4j) [![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0.txt) [![Docs](https://img.shields.io/badge/docs-latest-blue.svg)](http://viktorc.github.io/PP4J/)
While the standard Java libraries support multithreading based concurrency extensively, they do not support effective multiprocessing out of the box. In most situations, multithreading is more performant; however, the fact that threads share the address spaces of their parent processes means that care needs to be taken to ensure the threads can be safely run concurrently. Multiprocessing guarantees that execution units have their own address spaces and that no data is exchanged between them without explicit inter-process communication. This may be useful if a Java application has to _a)_ execute non-thread-safe or non-reentrant code concurrently or _b)_ invoke native code via JNI/JNA without the risk of crashing the main JVM. __PP4J__ (Process Pool for Java) is a multiprocessing library for Java that provides a flexible API and process executor implementations to help satisfy the above requirements.

## Java Process Pool
PP4J includes a Java process pool implementation that uses JVM instances to execute tasks in separate processes. This class, `JavaProcessPoolExecutor`, implements the `JavaProcessExecutorService` interface which extends the `ExecutorService` interface. This allows it to be used similarly to the standard Java thread pools with the only difference that the tasks submitted must implement the `Serializable` interface. This implicit requirement enables the pool to serialize and encode the tasks before sending them to the JVM instances for execution. The JVM instances return the results of the tasks—or the exceptions thrown—the same way, which requires the return values of the tasks to be serializable as well.
```java
JavaProcessConfig jvmConfig = new SimpleJavaProcessConfig(JVMType.CLIENT, 2, 8, 256);
JavaProcessExecutorService jvmPool = new JavaProcessPoolExecutor(new JavaProcessManagerFactory<>(jvmConfig), 10, 20, 2);
```
The code snippet above demonstrates the construction of a `JavaProcessPoolExecutor` instance. The first argument of the constructor is a `JavaProcessManagerFactory` instance that is responsible for creating the process managers for the pool's processes. The process manager factory's constructor takes an instance of the `JavaProcessConfig` interface, which allows for the definition of different settings to use for the JVMs. These settings include the architecture, type, minimum heap size, maximum heap size, and stack size of the JVM. Besides these, it also allows for the specification of the Java application launcher command if a simple `java` does not suffice, and for the definition of additional class paths to load classes from. Other, optional arguments of the process manager factory's constuctor include a serializable `Runnable` task that is executed in every Java process on startup, a wrap-up task of the same type that is executed in every process before it's terminated, and the timeout value of the Java processes which specifies after how many milliseconds of idleness the processes should be terminated. The first argument after the process manager factory is the minimum size of the pool. This is the minimum number of JVM instances the process pool will strive to maintain, even if the submission queue is empty. The second argument is the maximum size. The number of JVM instances maintained by the pool is guaranteed to never exceed this value. The third argument is the reserve size. This is the minimum number of available, i.e. idle, JVM instances the pool will strive to maintain at all times. It is important to note that the constructor of `JavaProcessExecutorService` blocks until the minimum number of JVM processes have successfully started up. Specifying a startup task negatively affects the startup times of the processes; however, it may significantly reduce initial submission execution delays by ensuring that the JVM instances load some of the required classes beforehand. Moreover, the JVM config can be used to limit the heap sizes of the JVM processes, thus enabling the running of a great number of them without taking up too much RAM.
```java
Random rand = new Random();
List<Future<Long>> futures = new ArrayList<>();
for (int i = 0; i < 10; i++) {
  futures.add(jvmPool.submit((Callable<Long> & Serializable) () -> {
    Thread.sleep(1000);
    return rand.nextLong();
  }));
}
for (Future<Long> future : futures) {
  System.out.println(future.get());
}
jvmPool.shutdown();
jvmPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
```
Lambda expressions may be defined as serializable by casting them to an intersection of types as shown in the above snippet. Although somewhat slower than multithreading, the execution of the code above still takes only a few milliseconds longer than the desired one second. This example also demonstrates the primary difference between multithreading and multiprocessing, i.e. processes have their own address spaces as opposed to threads. All the invocations of the `nextLong` method of `rand` return the same value as each process has its own copy of the object.

While it is possible to achieve good performance using the Java process pool, the overhead of starting up Java processes and getting the JVMs in high gear can be quite significant. The usage of native processes, whenever possible, allows for superior performance. If the objective is the parallel execution of non-thread-safe or non-reentrant native code, pools of native processes are almost always a better choice. They might require the writing of an executable wrapper program, but they eliminate the need for JNI/JNA and their performance exceeds that of Java process pools. The following section introduces the flexible API and process pool implementation that `JavaProcessPoolExecutor` is built upon.

## Process Pool
The high level design diagram below sums up the mechanics of the core process pool of the PP4J library. This process pool maintains a number of processes that implement a communication protocol over their standard streams (possibly to expose methods of a native library). It also accepts textual command submissions that it then delegates to available processes. These submissions honour the communication protocol as well and are responsible for handling the responses of the processes they have been delegated to. Through callback methods, the submissions notify their submitters when the processes are done processing them. The pool may also adjust its size dynamically to maintain its throughput. It does so via process managers that may or may not need to communicate with the processes. To explain how such a process pool can be set up, the following sections introduce the library's base API. 

![arch](https://user-images.githubusercontent.com/12938964/37467074-9a508dde-285f-11e8-9deb-395de62cd9e0.png)

All process pools of PP4J implement the `ProcessExecutorService` interface. The standard process pool, `ProcessPoolExecutor`, communicates with the processes via their standard streams. Instances of this process pool can be created by invoking the constructor directly. The first parameter of the constructor is an implementation of the `ProcessManagerFactory` functional interface for creating new instances of an implementation of the `ProcessManager` interface. These instances are responsible for specifying the processes and handling their startup and possibly termination. Other parameters include the minimum and maximum size of the pool and its reserve size. The size of the pool is always kept between the minimum pool size and the maximum pool size (both inclusive). Once the process pool is initialized, it accepts commands in the form of `Submission` instances which contain one or more `Command` instances. The submission is assigned to any one of the available processes in the pool. While executing a submission, the process cannot accept further submissions. The commands allow for communication with a process via its standard in and standard out/error streams. The implementation of the `Command` interface specifies the instruction to send to the process' standard in and handles the output generated by the process as a response to the instruction. Moreover, the implementation also determines when the instruction may be considered processed and therefore when the process is ready for the next instruction. The PP4J library also provides some standard implementations of the `ProcessManager`, `Submission`, and `Command` interfaces to allow for the concise definition of process pooling systems for typical situations.
```java
ProcessManagerFactory processManagerFactory = () -> new SimpleProcessManager(new ProcessBuilder("test.exe"),
    Charset.defaultCharset(),
    (outputLine, startupOutputStore) -> "hi".equals(outputLine),
    60000L,
    () -> new SimpleSubmission<>(new SimpleCommand("start", (outputLine, commandOutputStore) -> "ok".equals(outputLine))),
    () -> new SimpleSubmission<>(new SimpleCommand("stop", (outputLine, commandOutputStore) -> "bye".equals(outputLine))));
ProcessExecutorService pool = new ProcessPoolExecutor(processManagerFactory, 10, 50, 5);
```
In the example above, a process pool for instances of a program called "test.exe" is created. Every time the pool starts a new process, it waits until the message "hi" is output to the process' standard out, signaling that it has started up. By default, if the process outputs something to its standard error stream, the process manager considers the startup unsuccessful and throws a `FailedStartupException` which results in the shutdown of the pool. This behaviour can be configured by defining a predicate to handle the standard error output of the process during startup. If everything goes to plan, after a successful startup, the manager sends the instruction "start" to the process' standard in. The instruction "start" has the process perform some startup activities before it outputs "ok". Once this message is output to the process' standard out, the manager considers the process ready for submissions. By default, when something is output to the process' standard error stream during command execution, a `FailedCommandException` is thrown. This behaviour can be overriden by specifying an additional predicate for the standard error stream as the third argument of the constructor. Throwing a `FailedCommandException` from these predicates or from the `isCompleted` method (when implementing the `Command` interface directly) is indicative of the completion of the command and results in the abortion of the execution of the submission. Whenever the process needs to be terminated (either due to timing out or cancellation after the execution of a submission), the pool tries to terminate the process in an orderly way by sending it the "stop" instruction. If the response to this is "bye", the process is considered terminated. However, if something is printed to the process' standard error stream in response to the "stop" instruction, the process is killed forcibly. As specified by the fourth argument of the constructor of `SimpleProcessManager`, processes in the pool are terminated after 1 minute of idleness. The pool's minimum size is 10, its maximum size is 50, and its reserve size is 5.
```java
List<Future<?>> futures = new ArrayList<>();
for (int i = 0; i < 30; i++) {
  Thread.sleep(100);
  Submission<?> submission = new SimpleSubmission<>(new SimpleCommand("process 5",
      (outputLine, commandOutputStore) -> "ready".equals(outputLine)));
  futures.add(pool.submit(submission, true));
}
pool.shutdown();
pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
```
Once the pool is initialized, it is sent 30 instructions within 3 seconds. The instruction "process 5" has the process sleep for 5 seconds, printing "in progress" to its standard out every second except for the 5th second, when it prints "ready". This final output signals the completion of the execution of the command. The submissions above also result in the termination of the executing processes as denoted by the second, boolean parameter of the `submit` method of the pool. As the pool receives the submissions, it manages its size according to its minimum, maximum, and reserve size parameters. After the execution of the submissions, the pool is shutdown identically in behaviour to Java thread pools.

PP4J also allows for submissions to have return values. The result of a submission can be set, usually while processing the output of its commands, through the `setResult` method of the `AbstractSubmission` class. However, to be able to invoke the `setResult` method of the submission from the command implementations, the `AbstractSubmission` class must be extended and the commands must be defined in the implementation of the `getCommands` method. To make the definition of submissions with a return value simpler, the `SimpleSubmission` class has special constructors that take the result object as a parameter. As shown in the code snippet below, this result parameter can be mutated conveniently in the command definitions without the need for a reference to the submission instance. At last, the final result can be accessed through the `Future` instance returned by the process pool's `submit` method or directly through invoking the `getResult` method of the submission itself after its execution is completed.
```java
ProcessManagerFactory processManagerFactory = () -> new SimpleProcessManager(new ProcessBuilder("bash"),
    Charset.defaultCharset());
ProcessExecutorService pool = new ProcessPoolExecutor(processManagerFactory, 10, 10, 0);
List<Future<AtomicReference<String>>> futures = new ArrayList<>();
for (int i = 0; i < 10; i++) {
  AtomicReference<String> result = new AtomicReference<>();
  Command command = new SimpleCommand("echo user:$USER", (outputLine, commandOutputStore) -> {
    if (outputLine.startsWith("user:")) {
      result.set(outputLine.substring(5));
      return true;
    }
    return false;
  });
  Submission<AtomicReference<String>> submission = new SimpleSubmission<>(command, result);
  futures.add(pool.submit(submission));
}
for (Future<AtomicReference<String>> future : futures) {
  System.out.println(future.get().get());
}
pool.shutdown();
pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
```
The above examples demonstrate both the flexibility of the API and the effective usage of some of the default implementations of the `ProcessManager`, `Submission`, and `Command` interfaces for the concise definition of process pools and tasks to parallelize.

## Process Executor
Besides the process pools, PP4J also provides a standard implementation of the `ProcessExecutor` interface, `SimpleProcessExecutor` for the running of single processes without pooling. This, as presented below, allows for the synchronous execution of submissions in a single separate process with ease.
```java
ProcessManager processManager = new SimpleProcessManager(new ProcessBuilder("cmd.exe"), Charset.defaultCharset());
SimpleCommand command = new SimpleCommand("netstat & echo netstat done",
    (outputLine, commandOutputStore) -> "netstat done".equals(outputLine));
SimpleSubmission<?> submission = new SimpleSubmission<>(command);
try (SimpleProcessExecutor executor = new SimpleProcessExecutor(processManager)) {
  executor.start();
  executor.execute(submission);
  System.out.println(command.getJointStandardOutLines());
}
```

## Logging
PP4J uses [SLF4J](https://www.slf4j.org/) for logging which can be bound to different logging frameworks, such as [Logback](https://logback.qos.ch/), that usually allow for the configuration of both the format and granularity of logging. This can be really helpful when setting up complex process pools or executors to understand the behaviour of the managed processes and submissions.