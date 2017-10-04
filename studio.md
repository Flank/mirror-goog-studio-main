# Build Android Studio Source Code

If you haven’t already done so, [download the Android Studio source code](/source.md) so that you
have a local copy of the following projects:

 * Android plugin source code: `<studio-master-dev>/tools/adt/idea`
 * IntelliJ IDE base: `<studio-master-dev>/tools/idea/`
 * Shared library dependencies: `<studio-master-dev>/tools/base/`.

To learn how to build the Android plugin for Gradle from source, read
[The Android Gradle plugin](/build-system/README.md).

## Run Bazel
In order to successfully build Android Studio from source, you’ll need to make sure that you’re able
to run the revision-controlled executable of Bazel that's located in the
`<studio-master-dev>/tools/base/bazel/` directory:

```
$ cd studio-master-dev/
$ tools/base/bazel/bazel version
  Build label: …
  Build target: …
  …
```

To make sure Bazel can execute Android Studio build tasks, try running the following command:

```
$ bazel build <studio-master-dev>/tools/adt/idea/android:profiler-artifacts
```

To learn more about running Bazel (including running tests), read
[Building Studio with Bazel](bazel/README.md).


## Install prerequisites for Windows users

If you're on Windows, you need to complete the following steps before you build AS from the
command-line:

1. ___Install Python 2.7 or higher:___ Ensure you have Python installed on your windows machine, and
   the path to `python.exe` is added to your `PATH` environment variable.
   __Note:__ It is recommend you use the 64-bit version, although the 32-bit version should work
   fine.

2. __Install msys2:__ When building Android Studio, Bazel expects to find msys2 installed at
   `c:\tools\msys2\`. Otherwise, you get a build error.
3. __Install
   [Visual Studio 2015 build tools](http://landinghub.visualstudio.com/visual-cpp-build-tools)__:
   Otherwise, you get a build error.
4. __Install Git for Windows:__ If you haven’t already done so, download and install
   [Git for Windows (64-bit)](https://git-scm.com/downloads):
   1. Enable the option to __Use Git from the Windows Command Prompt__.
   1. Enable the option to __Checkout Windows-style, commit Unix-style line endings__.
   1. Add Git for Windows to your `PATH` environment variable.
   1. Configure Git to support long paths (that is, paths longer than 260 characters):
      ```
      C:> git config --global core.longpaths true
      ```
   1. Set the following PATH environment variables:
      ```
      C:> set PATH=%PATH%; %LOCALAPPDATA%\Programs\Git;%LOCALAPPDATA%\Programs\Git\Cmd;%LOCALAPPDATA%\Programs\Git\usr\bin;%USERPROFILE%\bin
      ```
5. __Download and install [git-repo](https://gerrit.googlesource.com/git-repo/)__ and include it in
   your `PATH` environment variable.
6. __Download the Android SDK.__ You can do this in one of two ways:
   1. Use the [SDK Manager](https://developer.android.com/studio/intro/update.html#sdk-manager) from
      a pre-installed version of Android Studio.
   1. Download the [SDK command line tools](https://developer.android.com/studio/index.html#Other),
      and then use the
      [sdkmanager command line tool](https://developer.android.com/studio/command-line/sdkmanager.html)
      to download an Android SDK package.

## Build Android Studio using IntelliJ

If you are interested in making changes to the Android Studio codebase and building the IDE from
source, you can do so [using IntelliJ](http://www.jetbrains.com/idea/documentation/).
To begin, you need to first configure a new project from the Android Studio source code, as follows:

1. Download and install the latest version of
   [IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/download).
2. Open IntelliJ IDEA and
   [create a new project](https://www.jetbrains.com/help/idea/new-project-wizard.html).
3. When prompted to select the source directory for your project, select
   `<studio-master-dev>/tools/idea/` and click __OK__.
4. Keep clicking __Next__ until you are prompted to select the project SDK.
5. Point IntelliJ to your local JDK, as follows:
   1. Select __IDEA jdk__ from the left pane.
   1. While __IDEA jdk__ is highlighted, click __Add new SDK (+) &gt; JDK__.
      __Note:__ The SDK you add should be a standard JDK, NOT an "IntelliJ Platform Plugin SDK".
   1. Navigate to where you downloaded the Android source code and select the JDK package included
      in `prebuilts/studio/jdk/`
   1. If you are on __Linux or Windows__, also add `<jdk-path>/lib/tools.jar` to the __IDEA jdk__
      classpath.
6. Keep clicking __Next__ until IntelliJ creates your project.
7. After the IntelliJ finishes creating your project, click __Build &gt; Rebuild Project__ from the
   menu bar.
   * If you see issues compiling .kt files, make sure that you've installed the Kotlin plugin
8. Select __AndroidStudio__ from the __Configurations__ pulldown menu near the top right of the IDE.

__Note:__ If you run into issues compiling `*.groovy` files, make sure you
[enable the Groovy plugin](https://www.jetbrains.com/help/idea/getting-started-with-groovy.html).

To build Android Studio, click the __Run__ button (which looks like a green ‘play’ button) near the
top right corner of the IDE.

## Building from the command line

To build Android Studio from the command line, run the bash shell as Administrator and execute the
following commands:

```
$ cd studio-master-dev/tools/idea
$ ./build_studio.sh
```

__Windows users:__ to build Android Studio from the command line from a Windows machine, run
Command Prompt as Administrator and execute the following commands:

```
$ cd studio-master-dev/tools/idea
$ ant
```

You should find compressed build artifacts in `studio-master-dev/out/artifacts/`.
To run the version of Android Studio you just built, extract the artifact for your OS,
and then run either `/bin/studio.sh` or `\bin\studio.exe` from the extracted directory.


## Common issues

* __Error: java: package com.sun.source.tree does not exist__: Make sure to add `tools.jar` to the
"IDEA jdk" configuration, as explained above in [Build Android Studio using IntelliJ](#build-android-studio-using-intellij).
* __java.lang.UnsatisfiedLinkError: C:\cygwin64\home\...\tools\idea\bin\win\jumplistbridge64.dll:
  Access is denied__:
  You may get this exception if you are trying to load and run the IDE project on Windows using
  Cygwin. To fix this issue, grant execute permissions to necessary files by opening a command
  prompt as Administrator and running the following command:

  ```
  icacls C:\cygwin64\home\Android\aosp\tools\idea\bin\win\* /grant Everyone:(RX)
  ```

  Alternatively, you can navigate to the `tools\bin\win\` directory and perform the following
  actions for each `.exe` and `.dll` file:
  1. Right-click on a file.
  2. Select __Properties__ to open a dialog.
  3. Click on the __Security__ tab.
  4. Click __Edit__.
  5. Enable execute permission.
  6. Click __OK__.
