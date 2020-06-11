Aspects agent
======

## Overview

This is a [java agent](https://docs.oracle.com/javase/10/docs/api/java/lang/instrument/package-summary.html) that allows to add functionality to methods in the loaded classes. In order to use it, simply add the -javaagent option as follows
```
-javaagent:/tmp/slow-down-agent-1.0-SNAPSHOT.jar=/tmp/rules.txt
```

Make sure that the name of the jar file matches the given in the example. After the equals, the agent expects [the rules file](#the-rules-file).

### The rules file
The rules file defines which methods should be intercepted and the method to be called when the method is called.
This allows for additional checks or logging to be added.

The rules file is a JSON with the following format:

```json
{
  "methods":[
    {
      "name":"sun.net.www.protocol.http.HttpURLConnection.getInputStream()Ljava/io/InputStream;",
      "aspect":"com.android.tools.checker.Assertions#warn"
    },
    ...
  ],
  "annotations":[
    {
      "name":"@com.android.annotations.concurrency.UiThread",
      "aspect":"com.android.tools.checker.AspectsLogger#logIfNotEdt",
      "group":"threading"
    },
    ...
  ]
}
```

In this case, when method ```getInputStream``` of ```HttpURLConnection```, the static method ```com.android.tools.checker.Assertions.warn()``` will be called.
Since ```com.android.tools.checker.Assertions``` is a common source of methods, if you omit it, this will be the default class used by the rules file.

If you do not want to intercept only a specific signature but all the methods with the name name,
you can just use the simplified signature and not add the parameters and return type. The example above would end up as:

```json
...
    {
      "name":"sun.net.www.protocol.http.HttpURLConnection.getInputStream",
      "aspect":"com.android.tools.checker.Assertions#warn"
    },
...
```

Additionally you can also use annotations as a way to add certain behaviours. In the example above,
the agent will intercept each method annotated with `@UiThread` and call `com.android.tools.checker.AspectsLogger#logIfNotEdt`
to make sure it's not called outside EDT. Please note all annotations need to be specified with a leading `@`.

It's worth mentioning that an annotation can also specify a `group`. That's useful to make the agent
aware of which annotations are related and probably shouldn't be used together. For instance, it doesn't make sense to annotate a method with both
`@UiThread` and `@Slow`, so these annotations should probably belong to the same group. If the group
is specified for an annotation, it's not mandatory to provide the `aspect` field. This is useful
when you want to prevent annotation conflicts without necessarily intercepting annotated methods. For
example, we could add `@AnyThread` to the same group as `@UiThread` and `@Slow` without intercepting
the methods that can run on any thread.

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
```json
{
  "methods":[
    {
      "name":"sun.net.www.protocol.ftp.Handler.openConnection",
      "aspect":"#warn"
    },
    {
      "name":"java.net.URL.openConnection",
      "aspect":"#warn"
    },
    {
      "name":"java.nio.file.Files.walkFileTree",
      "aspect":"com.android.tools.checker.AspectsLogger#logIfEdt"
    }
  ],
  "annotations":[
    {
      "name":"@com.android.annotations.concurrency.Blocking",
      "aspect":"#assertIsNotEdt",
      "group":"threading"
    },
    {
      "name":"@com.android.annotations.concurrency.UiThread",
      "aspect":"com.android.tools.checker.AspectsLogger#logIfNotEdt",
      "group":"threading"
    },
    {
      "name":"@com.annotations.custom.MyAnnotation",
      "aspect":"something.else#fail"
    }
  ]
}
```

### Baseline file

In addition to the rules file, you can also specify a **baseline** file to allow some known offender stack traces.
That is particularly useful to enable the agent in a project that already violates some of the rules defined in the rules file,
in order to prevent future violations. To specify the baseline file, you should change the invocation of the agent to the following format:

```
-javaagent:/tmp/slow-down-agent-1.0-SNAPSHOT.jar=/tmp/rules.txt;/tmp/baseline.txt
```

The baseline is a text file containing one offending stack trace per line in the following format:

```
com.android.tools.idea.model.MergedManifestInfo.mergeManifests|com.android.tools.idea.model.MergedManifestInfo.create
```

If the line is present in the baseline, the agent will ignore rules matching *MergedManifestInfo#mergeManifests* if this method is called
by *MergedManifestInfo#create*.

## How to build the agent

Use bazel to build the agent:

```
bazel build //tools/base/aspects_agent:aspects_agent
```

Now the agent is located at:

```
$SRC/bazel-bin/tools/base/aspects_agent/aspects_agent.jar