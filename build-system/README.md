The Android Gradle Plugin
=========================

This page describes how to build the Android Gradle plugin, and to test it.

[TOC]

## Get the Source Code

Follow the instructions [here](../source.md) to checkout the source code.

Once you have checked out the source code, the Gradle Plugin code can be found
under `tools/base`

## Building and editing the Android Gradle plugin

To edit the plugin import the Gradle project rooted in the `tools` directory in
to the 
[most recent Intellij IDEA EAP](https://www.jetbrains.com/toolbox-app/).

You can run unit and integration tests directly from within Intellij IDEA. 
You can also run them with Gradle
[from the command line](from-the-command-line.md)

*** aside
All of the projects are built together in a multi-module Gradle project setup.
The root of that project is `tools/`
***

To build AGP for use in other projects, use the "Execute Gradle Task" action to
run `:publishAndroidGradleLocal` (or `:publishLocal` if you also need the
databinding libraries)

The above command publishes the plugin to a local Maven repository located in
`../out/repo/`, and it is also done as part of running the integration tests.

### Debugging the Android Gradle plugin

To debug unit tests simply use the debug action in Intellij IDEA.

To debug the Android Gradle Plugin being
run from integration tests:

1. Add the environment variable `DEBUG_INNER_TEST=1`
   to the run configuration
2. Run the test in IDEA (Run, not debug, unless you want to debug both
   the test and the plugin at the same time)
3. Connect the Intellij
   [remote debugger](https://www.jetbrains.com/help/idea/tutorial-remote-debug.html#Tutorial__Remote_debug-5-chapter)
   to port **5006**

### Using locally built plugin in a project

To test your own Gradle projects, using your modified Android Gradle plugin,
modify the build.gradle file to point to your local repository
(where the above publishLocal target installed your build).

For example, if you ran the repo init command above in `/my/aosp/work`, then
the repository will be in `/my/aosp/work/out/repo`.

You may need to change the version of the plugin as the version number used in
the development branch is typically different from what was released.  You can
find the version number of the current build in
`tools/buildSrc/base/version.properties.`

For example:

|||---|||
#### Before
```
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:4.2.0'
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
```

#### After

```
buildscript {
    repositories {
        maven { url '/my/aosp/work/out/repo' }
        google()
        mavenCentral()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:7.0.0-dev'
    }
}

allprojects {
    repositories {
        maven { url '/my/aosp/work/out/repo' }
        google()
        mavenCentral()
    }
}
```
|||---|||

To debug a project like this simply run

```
$ ./gradlew --no-daemon -Dorg.gradle.debug=true someTask
```
and connect a remote debugger to port `5005`.

### Preparing your changes for submission

If you've made changes, make sure you run the tests to ensure you haven't broken anything:

```
cd base/build-system && ../../gradlew test
```

Presubmit runs all the tests, so another strategy is to guess which tests may
be affected by your change and run them locally but rely on the presubmit tests
to run all the integration tests.
