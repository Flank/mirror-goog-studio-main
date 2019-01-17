Aspects agent
======

## Overview

This is a [java agent](https://docs.oracle.com/javase/10/docs/api/java/lang/instrument/package-summary.html) that allows to add functionality to methods in the loaded classes. In order to use it, simply add the -javaagent option as follows
```
-javaagent:/tmp/slow-down-agent-1.0-SNAPSHOT.jar=/tmp/rules.txt
```

Make sure that the name of the jar file matches the given in the example. After the equals, the agent expects [the rules file](#the-rules-file).

### The rules file
The rules file defines which methods should be intercepted and the method to be called when the method is called. This allows for additional checks or logging to be added.

An example of rules file would be:
```
sun.net.www.protocol.http.HttpURLConnection.getInputStream()Ljava/io/InputStream;=com.android.tools.checker.Assertions#warn
```

In this case, when method ```getInputStream``` of ```HttpURLConnection```, the static method ```com.android.tools.checker.Assertions.warn()``` will be called.
Since ```com.android.tools.checker.Assertions``` is a common source of methods, if you omit it, this will be the default class used by the rules file.

If you do not want to intercept only a specific signature but all the methods with the name name, you can just use the simplified signature and not add the paramters and return type. The example above would end up as:
```
sun.net.www.protocol.http.HttpURLConnection.getInputStream;=com.android.tools.checker.Assertions#warn
```

### com.android.tools.checker.Assertions
This file defines a few common assertions.

|Method name         |Usage                                   |
|--------------------|----------------------------------------|
|assertIsNotEdt      |This method will throw a ```RuntimeException``` if the method is called on the EDT|
|assertIsEdt      |This method will throw a ```RuntimeException``` if the method is *NOT* called on the EDT|
|warn                |Prints a string to ```System.err``` when this method is called|
|dumpStackTrace      |Prints the current stack trace to ```System.err```|

### Defining your own assertions
The only requirement for the methods are to be public and static. As long as they are part of the classpath, they can be invoked by the agent.

### Sample rules file
```
# Network
sun.net.www.protocol.ftp.Handler.openConnection=#warn
sun.net.www.protocol.http.Handler.openConnection=#warn
sun.net.www.protocol.https.Handler.openConnection=#warn
#java.net.URL.openConnection=#warn

# Long I/O
java.nio.file.Files.walkFileTree=#warn
java.nio.file.Files.walk=#warn

# VFS Methods
com.intellij.openapi.vfs.VfsUtil.copyDirectory=#warn
com.intellij.openapi.vfs.VfsUtil.copy=#warn
```

## How to build the agent

Use bazel to build the agent:

```
bazel build //tools/base/aspects-agent:aspects_agent
```

Now the agent is located at:

```
$SRC/bazel-genfiles/tools/base/aspects-agenent/aspects_agent.jar