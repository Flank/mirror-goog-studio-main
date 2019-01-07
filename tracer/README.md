# Description

This package contains a lightweight utility to add trace points to java applications.

## How to add trace points

There are several ways to add trace points to an application

### Direct API usage

For ultimate control of where the events are created:


```java
public void foo() {
    Trace.begin("Start expensive foo");
    ...
    Trace.end();
}
```

Notice that every started event must be explicitly ended.

### Try-with-resources

However, in order to make sure an event is properly closed if exceptions are thrown, it might be more convinent to use:

```java
public void foo() {
    try (Trace ignored = Trace.begin("Start expensive foo")) {
        ...
    }
}
```

### Annotations

If the method name is enough as the event tag, an even easier way is to use annotations:

```java
import com.android.annotations.Trace;

@Trace
public void foo() {
    ...
}
```

### A trace profile

The last way of instrumenting is by building a profile file:

```
Output: /tmp/report.json
Trace: com.package.Clazz::method
Trace: com.package.Clazz
Trace: com.package.*
Flush: com.package.Clazz::end
```

For those cases where the code is not available or recompiling is not an options it is possible to set up a file describing what methods want to be traced.

* ``Start`` before tracing this method, the output file will be cleared.
* ``Output`` The output .json report file, or ```/tmp/report.json``` if unspecified.
* ``Trace`` allows specifying which class, method or package will be instrumented.
* ``Annotation`` if a method is annotated with this it will be traced, by default only ```com.android.annotations.Trace``` is traced.
* ``Flush`` at the end of which method will a synchronous flush be performed.
* ``Trace-Agent`` if set to ```true``` a special event is traced from the agent's ```premain``` to the VM shutdown. 

## How to build the tracing agent

Use bazel to build the tracing agent:

```
bazel build //tools/base/tracer:trace_agent
```

Now the agent is located at:

```
$SRC/bazel-genfiles/tools/base/tracer/trace_agent.jar
```

## How to enable tracing

Tracing is performed when the tracing agent is attached at startup to an application.
To attach to studio simply add to the run configuration the following:

```
-javaagent:../../../bazel-genfiles/tools/base/tracer/trace_agent.jar
```

If you want to pass a profile file then:

```
-javaagent:../../../bazel-genfiles/tools/base/tracer/trace_agent.jar=../../base/tracer/deploy.profile
```

Note that there is special code to automatically forward the tracing agent down to the gradle daemons.

In a BUILD file, the following can be added to have a ``java_binary`` always tracing:

```
jvm_flags = ["-javaagent:$(location //tools/base/tracer:trace_agent)"],
```

## How to use tracing in Gradle directly

In your gradle project directory, edit gradle.properties file and add:

```
org.gradle.jvmargs = "-javaagent:$SRC/bazel-genfiles/tools/base/tracer/trace_agent.jar"
```

Where $SRC is your studio-master-dev full path.

In order to use a trace profile:

```
org.gradle.jvmargs = "-javaagent:$SRC/bazel-genfiles/tools/base/tracer/trace_agent.jar=/path/to/profile.json"
```

It might be necessary to stop gradle daemons to force a reload of your profile:

```
./gradlew --stop
```

## The report

The report is saved by default to /tmp/report.json. To open navigate to chrome://tracing and load the file.
