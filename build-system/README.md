The Android Gradle Plugin
=========================

This page describes how to build the Android Gradle plugin, and to test it.

# Get the Source Code

Follow the instructions [here](http://tools.android.com/build) to get checkout the source code.

Once you have checked out the source code, the Gradle Plugin code can be found under `tools/base`

# Building the plugin

All of the projects are built together in a multi-module Gradle project setup.
The root of that project is `tools/`

The Gradle Plugin is currently built with Gradle 4.0. To ensure you are using the right version,
please use the gradle wrapper scripts (gradlew) at the root of the project to build
([more Gradle wrapper info here](http://gradle.org/docs/current/userguide/gradle_wrapper.html))

You can build the Gradle plugin (and associated libraries) with

```$ ./gradlew assemble```

If assemble fails the first time you execute it, try the following

```$ ./gradlew clean assemble```

To test the plugin, you need to run the following command
```$ ./gradlew check```

Additionally, you should connect a device to your workstation and run:
```$ ./gradlew connectedIntegrationTest```

To run a specific connectedIntegrationTest, run:
```$ ./gradlew connectedIntegrationTest -D:base:integration-test:connectedIntegrationTest.single=BasicTest```

## Editing the plugin

The code of the plugin and its dependencies is located in `tools/base`.
You can open this project with IntelliJ as there is already a `tools/base/.idea` setup.

There are tests in multiple modules of the project.
`tools/base/build-system/integration-test` contains the integration tests and compose of the
majority of the testing of the plugin.
To run the integration tests. run:
```$ ./gradlew :base:integration-test:test```

To run just a single test, you can use the --tests argument with the test class you want to run.  e.g.:
```$ ./gradlew :b:integ:test --tests *.BasicTest```

or use the system property flag (see Gradle docs for the difference: link, link):
```$ ./gradlew :b:integ:test -D:base:integration-test:test.single=BasicTest```

To compile the samples manually, publish the plugin and its libraries first with
```$ ./gradlew publishLocal```
(Tip: you can use camelcase prefixes for target names,
so for the above you can just run gradlew pL).
(Also, running `check`, `:base:integration-test:test`, and `connectedIntegrationTest` does
publishLocal first).

## Debugging

For debugging  unit tests, you can use the following:
```$ ./gradlew :base:gradle:test --debug-jvm --tests='*.BasicTest'```

For debugging integration tests code (not the Gradle code being executed as part of the test):
```$ ./gradlew :b:integ:test --debug-jvm -D:base:integration-test:test.single=BasicTest```

For debugging plugin code when run locally:
```$ cd a-sample-project  # Make sure build.gradle points at your local repo, as described below.
$ ./gradlew --no-daemon -Dorg.gradle.debug=true someTask
```

If you need to debug an integration test while running within the integration tests framework,
you can do :
```
$ DEBUG_INNER_TEST=1 ./gradlew :b:integ:test -D:base:integration-test:test.single=ShrinkTest # to run and debug only one test. --tests should also work.
```

This will silently wait for you to connect a debugger on port 5006. You can combine this with
`--debug-jvm` flag (which expects a debugger on port 5005) to debug both the sides of the tooling
API at the same time.

# Using locally built plugin

To test your own Gradle projects, using your modified Android Gradle plugin,
modify the build.gradle file to point to your local repository
(where the above publishLocal target installed your build).

In other words, assuming your build.gradle contains something like this:

```
buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:2.3.0'
    }
}
```

You need to point to your own repository instead.
For example, if you ran the repo init command above in `/my/aosp/work`, then the repository will be
in `/my/aosp/work/out/repo`. 

You may need to change the version of the plugin as the version number
used in the development branch is typically different from what was released.
You can find the version number of the current build in `tools/buildSrc/base/version.properties.`

```
buildscript {
    repositories {
        maven { url '/my/aosp/work/out/repo' }
        jcenter()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:3.0.0-dev'
    }
}
```

If you've made changes, make sure you run the tests to ensure you haven't broken anything:

```
./gradlew pL base:gradle:test base:gradle-core:test base:integration-test:test
```
