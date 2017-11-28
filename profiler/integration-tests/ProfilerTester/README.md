# Android Studio ProfilerTester App

ProfilerTester is an Android app used by the dev and QA teams to test Android
Studio profilers.

## Setup Instructions

Open the project in Android Studio. Make sure you have the latest Android SDK
installed.

Make sure CMake is installed: go to Settings, open Appearance & Behavior >
System Settings > Android SDK, click on the SDK Tools tab and check CMake. Click
OK to install the tool.

To build the project, use Build > Make Project. If Gradle sync fails, make sure
the <code>distribtuionUrl</code> in <b>gradle-wrapper.properties</b> is still
valid. Otherwise update it with a valid one from
https://service.gradle.org/distributions.

## Using the app

1.  Profile the app in Android Studio
2.  Use the primary dropdown to select the profiler you want to test
3.  Use the secondary dropdown to select the task to run
4.  Click the "play" button to run the task. Observe the profilers in Android
    Studio
5.  Click the "forward" or "rewind" buttons to swtich to another tasker or
    profiler

## Exporting zip for the QA team

Whenever you update the app code or config, drop a zip so the QA team (who may
not have Git access) can use it.

1. In Android Studio, use File > Export to Zip File to generate a zip of the
   project files
2. Delete the app/.externalNativeBuild folder from the zip file
3. Copy the file to tools/adt/idea/manual-tests/res/perf-tools and overwrite
   ProfilerTester.zip
4. Send a CL and check in the updated zip file
