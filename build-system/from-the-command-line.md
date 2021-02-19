The Android Gradle Plugin: Command Line
=======================================

We recommend [using Intellij IDEA](README.md) to edit the Android Gradle Plugin.

## Building from the command line

To ensure you are using the right version of Gradle, please use the
[`./gradlew` Gradle wrapper script](http://gradle.org/docs/current/userguide/gradle_wrapper.html))
in the `tools/` directory to build.

To build the Android Gradle Plugin, run (from the `tools/` directory)

```
$ ./gradlew :publishAndroidGradleLocal
```

***aside
(Tip: Gradle allows camel-case abbreviations for task names.  So, to execute
the command above, you can simply run `./gradlew :pAGL`).
***

The above command publishes the plugin to a local Maven repository located in
`../out/repo/`

To build the Android Gradle Plugin with the data binding runtime libraries, run `./gradlew :publishLocal`

### Test your build

To run the tests for everything built with Gradle, including the local build of
the plugin, run the following command

```
$ ./gradlew check
```

To run a specific integration test, run:

```
$ ./gradlew :base:build-system:integration-test:<integration test module>:<integration test task name> \
    --tests=<specific integration test>
```

### Editing the plugin

The code of the plugin and its dependencies is located in `tools/base`.
We recommend [using Intellij IDEA](README.md) to edit the Android Gradle Plugin.

There are tests in multiple modules of the project.
`tools/base/build-system/integration-test` contains the integration tests and
compose of the majority of the testing of the plugin.  To run the integration
tests. run:

```$ ./gradlew :base:build-system:integration-test:application:test```

To run just a single test, you can use the --tests argument with the test class
you want to run.  e.g.:

```$ ./gradlew :b:b-s:integ:app:test --tests *.BasicTest```

To compile the samples manually, publish the plugin and its libraries first
with `$ ./gradlew :publishLocal` (Also, running `check`,
`:base:build-system:integration-test:application:test`, etc. first run
`:publishAndroidGradleLocal` and `:publishLocal` as needed).

### Debugging

For debugging unit tests, we recommend directly [using Intellij
IDEA](README.md).

You can do the same from the command line: use the following to allow
a remote debugger to be attached.
```
$ ./gradlew :base:gradle:test --debug-jvm --tests='*.BasicTest'
```

For debugging plugin code when run locally:
```
$ cd a-sample-project  # Make sure build.gradle points at your local repo,
                       # as described in the main README.
$ ./gradlew --no-daemon -Dorg.gradle.debug=true someTask
```

If you need to debug an integration test while running within the integration
tests framework, you can do:

```
$ DEBUG_INNER_TEST=1 ./gradlew :b:b-s:integ:app:test --tests TestTaskClassName
```

This will silently wait for you to connect a debugger on port 5006. You can
combine this with `--debug-jvm` flag (which expects a debugger on port 5005) to
debug both the sides of the tooling API at the same time.

