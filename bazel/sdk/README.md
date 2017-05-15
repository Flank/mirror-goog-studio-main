# Development Android SDK

[TOC]

Note: This document provides some high level information first, but you may wish to skip directly to
the "Updating the development SDK" section below if you don't care about any of that.

_This documentation does not currently support AOSP yet. Further instructions will be added later._

Files of interest:

|   |   |
|---|---|
| `dev-sdk-packages` | A list of all SDK components needed by the Studio codebase |
| `prebuilts.studio.sdk.BUILD` | Bazel rules for breaking the SDK up into its parts|
| `src/...` | Source for a utility that updates the SDK for all hosts |
| `all_sdks_manifest.xml` | An manifest extender which pulls down all SDKs into your local repo |

## Overview

This directory contains utilities and other support files which allow us to depend on the Android
SDK in our code and tests in a way that is _hermetic_ and _deterministic_.

Code is _hermetic_ if it declares its dependencies explicitly. That is, if you have a test that
needs to run against a specific version of some subcomponent of an SDK, it should say so and, if
that subcomponent is missing, the test should fail before it even starts. This is in contrast to a
test that just points at a generic SDK directory and fails at runtime if a file it wants is missing.

Code is _deterministic_ if running it repeatedly always produces the same result. For example, if
you sync to a git project at any time in its past, code should still build and tests should still
pass. However, if you depend on an external resource, like having a process which, after syncing,
hits a server for the latest version of its resources, you might find that your old code starts
failing due to changed assumptions over time.

## Development SDK location

For unit tests and some compile-time dependencies, we provide a read-only SDK in our codebase. After
you do a `repo sync`, and depending on your OS host, you will your platform specific sdk pulled down in your workspace:

```
//prebuilts/studio/sdk/host
```

Note that this is a symlink to the platform dependent directory.
If you find yourself developing an Android app inside Android Studio, you should _not_ use this SDK
for that. Instead, you should download a separate, mutable SDK (e.g. in `~/Android/sdk`) which
you can update and prune to your hearts content.

## BUILD

This directory includes a bazel `BUILD` file which simply contains a bunch of
[filegroup](http://bazel.io/docs/be/general.html#filegroup) rules for exposing various SDK
components, for example targets such as `//prebuilts/studio/sdk:platform-tools` and
`//prebuilts/studio/sdk:build-tools/23.0.1`.

This file ultimately belongs in `//prebuilts/studio/sdk`, but, as that's really the only file the
folder would contain, it's not worth creating a git project just for that.

Instead, the `BUILD` file lives here, as `prebuilts.studio.sdk.BUILD`, and is automatically copied
by `repo` when anyone does a `sync` on this project. Here's the relevant snippet from our repo
manifest:

```
# $root/.repo/manifests/default.xml
...
<project path="tools/base" name="platform/tools/base">
   ...
   <copyfile
      src="bazel/sdk/prebuilts.studio.sdk.BUILD"
      dest="prebuilts/studio/sdk" />
 </project>
```

## dev-sdk-packages

This directory includes a `dev-sdk-packages` file which contains a list of SDK components that one
would need if they wanted to compile all code and pass all tests.

Here's a snippet from the top of the file:
```
tools
platform-tools
build-tools;24.0.0
...
```

This file will be used to update and download SDK components. For future maintainers, please keep it
up to date if you need to add any new SDK packages.

## Filtering SDKs

As of the time of writing this document, the development SDK, raw, is 2.5G (per each host, or
7.5G total). This is bound to grow larger over time. However, we may only need a fraction of the
actual SDK. For example, ddmlib tests only need the `adb` binary and nothing else from
`platform-tools`.

Therefore, the `dev-sdk-updater` binary supports adding an optional filter-by-glob for each package.
To accomplish this, append a filter after a colon to the desired package, e.g.
`platform-tools:adb*`. Use curly braces if you need to list multiple globs,
e.g. `{pattern1,pattern2}`, which will match if _any_ of the patterns match.

See the [official docs on globs](http://docs.oracle.com/javase/tutorial/essential/io/fileOps.html#glob)
and `dev-sdk-updater --help` for more details.

## Updating the development SDK

This section provides steps needed to update the development SDK. To approach this with a concrete
example, imagine we need to add `build-tools/19.0.3` (which is actually an obsolete package, so
please don't do this!).

### Pull down all three SDK repositories

By default, the repo manifest will only pull down the one SDK repository that matches your host
machine. To work around this, this folder provides an `all_sdks_manifest.xml` you can copy into your
`local_manifests` directory. Afterwards, you can `repo sync` into the `studio-master-dev` folder you
work out of normally, and it will start pulling down all SDKS, not just the one that matches your
host OS.

```
$ cd /path/to/studio-master-dev/

$ mkdir .repo/local_manifests/
$ cp tools/base/bazel/sdk/all_sdks_manifest.xml .repo/local_manifests/

$ repo sync
```

You only have to do this step once.

### Start a new branch

```
$ cd /path/to/studio-master-dev/

# This branch name will become the topic name for your CLs so choose a unique name.
# Of course, your branch name will be different... 
$ repo start devsdk+build-tools+19_0_3 \
    prebuilts/studio/sdk/darwin \
    prebuilts/studio/sdk/linux \
    prebuilts/studio/sdk/windows \
    tools/base
```

### Add a new package

If you need to add a new package, then, using your favorite editor, open
`tools/base/bazel/sdk/dev-sdk-packages` and modify it. Here, we add `build-tools;19.0.3`.

```
...
build-tools;20.0.0
build-tools;19.1.0
build-tools;19.0.3
platforms;android-24
platforms;android-23
...
```

Next, we'll need to run SDK tools to fetch the new data.

```
$ cd /path/to/studio-master-dev/

$ bazel run //tools/base/bazel/sdk:dev-sdk-updater
```

### Add a new filegroup rule

Open `prebuilts.studio.sdk.BUILD`. Once checked in, this will automatically get copied to
`//prebuilts/studio/sdk/BUILD` by repo at sync time.

Add a new filegroup somewhere in the file:
```
filegroup(
  name = "build-tools/19.0.3",
  srcs = glob(
    include = ["*/build-tools/19.0.3/**"],
  ),
  visibility = ["//visibility:public"],
)
```

Here, `*/build-tools/19.0.3` will match with `darwin/build-tools/19.0.3` on mac,
`linux/build-tools/19.0.3` on linux, etc.

To test that you set the rule correctly, manually copy the `BUILD` file yourself and do a query:

```
$ cd /path/to/studio-master-dev/

$ cp tools/base/bazel/sdk/prebuilts.studio.sdk.BUILD \
     prebuilts/studio/sdk/BUILD
$ bazel query "deps(//prebuilts/studio/sdk:build-tools/19.0.3)"
```

See also: [bazel filegroup rule](http://bazel.io/docs/be/general.html#filegroup)

### Make sure tests still pass

```
$ cd /path/to/studio-master-dev/tools/base/bazel

$ ./bazel test $(<test_targets)
```

### Upload code review

```
$ cd /path/to/studio-master-dev/

$ repo forall \
    prebuilts/studio/sdk/darwin \
    prebuilts/studio/sdk/linux \
    prebuilts/studio/sdk/windows \
    tools/base \
    -c git add -A; git commit -a -m "Updated SDK with build-tools;19.0.3"

# When uploading, include -t to ensure all code reviews have the
# same topic (the topic will be set to your current branch name).
# Be careful that someone else isn't using the same topic as you
# at the same time!
$ repo upload --cbr -t

# This opens an editor. Uncomment all SDK branches and save.
```

### Cleanup

_(Optional)_ Once your CLs are submitted, you can delete local data if you don't plan to update the
SDK again any time soon.

```
$ cd /path/to/studio-master-dev/
$ rm .repo/local_manifests/all_sdks_manifest.xml

# Delete two of the three SDKs
$ rm -rf prebuilts/studio/sdk/darwin # unless you're darwin
$ rm -rf prebuilts/studio/sdk/linux # unless you're linux
$ rm -rf prebuilts/studio/sdk/windows # unless you're windows
```
