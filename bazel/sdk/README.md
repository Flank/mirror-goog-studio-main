# Development Android SDK

[TOC]

Note: This document provides some high level information first, but you may wish to skip directly to
the "Updating the development SDK" section below if you don't care about any of that.

_This documentation does not currently support AOSP yet. Further instructions will be added later._

Files of interest:

|   |   |
|---|---|
| `dev-sdk-packages` | A list of all SDK components needed by the Studio codebase |
| `sdk.gitignore` | A master gitignore to be copied into all SDKs |
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
you do a `repo sync`, and depending on your OS host, you will have one of three paths pulled down
into your project.

```
//prebuilts/studio/sdk/darwin
//prebuilts/studio/sdk/windows
//prebuilts/studio/sdk/linux
```

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

## sdk.gitignore

As of the time of writing this document, the development SDK, raw, is 2.5G (per each host, or
7.5G total). This is bound to grow larger over time. However, we may only need a fraction of the
actual SDK. For example, ddmlib tests only need the `adb` binary and nothing else from
`platform-tools`.

If only there was a way to filter out files that we didn't care about...

Of course, that's where `.gitignore` comes in. We use git's ignore functionality to blacklist
almost all of the SDK, only exposing the parts of it our codebase actually needs.

The only issue here is that all three git repositories should share the exact same `.gitignore`
file. For example, if `linux` and `darwin` had different `.gitignore`s, a test on one platform might find a
file while the other platform fails.

If anyone ever needs to modify the `.gitignore` rules, they should edit the master file living here
and run `dev-sdk-updater` (see below) to copy them over.

## Updating the development SDK

This section provides steps needed to update the development SDK. To approach this with a concrete
example, imagine we need to add `build-tools/19.0.3` (which is actually an obsolete package, so
please don't do this!).

### Pull down all three SDK repositories

By default, the repo manifest will only pull down the one SDK repository that matches your host
machine. There are two ways to work around this.

#### Option 1: repo init -b studio-master-dev-sdk

The `studio-master-dev-sdk` branch is a special one that only contains just the projects needed to
update the SDK.

```
$ mkdir -p /path/to/studio-master-dev-sdk/

$ repo init ... -b studio-master-dev-sdk
$ repo sync
```

#### Option 2: all_sdks_manifest.xml

This folder provides an `all_sdks_manifest.xml` you can copy into your `local_manifests` directory.
With this approach, you can `repo sync` into the `studio-master-dev` folder you work out of
normally, and it will start pulling down all SDKS, not just the one that matches your host OS.

```
$ cd /path/to/studio-master-dev/

$ mkdir .repo/local_manifests/
$ cp tools/base/bazel/sdk/all_sdks_manifest.xml .repo/local_manifests/

$ repo sync
```

### Start a new branch

```
$ cd /path/to/studio-master-dev/ # or studio-master-dev-sdk

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
$ cd /path/to/studio-master-dev/ # or studio-master-dev-sdk

$ bazel build //tools/base/bazel/sdk:dev-sdk-updater
$ ./bazel-bin/tools/base/bazel/sdk/dev-sdk-updater \
  --package-file tools/base/bazel/sdk/dev-sdk-packages \
  --dest prebuilts/studio/sdk
```

### Modify sdk.gitignore

At this point, our new build-tools directory isn't showing up because it's being ignored by git!

Let's modify `sdk.gitignore`. At the end, let's add a `!` whitelist entry:

```
...
!build-tools/19.0.3
```

_See also: [gitignore docs](https://git-scm.com/docs/gitignore)_

And finally, let's propagate those changes:

```
$ cd /path/to/studio-master-dev/ # or studio-master-dev-sdk

$ bazel build //tools/base/bazel/sdk:dev-sdk-updater
$ ./bazel-bin/tools/base/bazel/sdk/dev-sdk-updater \
  --gitignore tools/base/bazel/sdk/sdk.gitignore \
  --dest prebuilts/studio/sdk
```

### Add a new filegroup

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

To test that this is working, manually copy it yourself and do a query:

```
$ cd /path/to/studio-master-dev/ # or studio-master-dev-sdk

$ cp tools/base/bazel/sdk/prebuilts.studio.sdk.BUILD \
     prebuilts/studio/sdk/BUILD
$ bazel query "deps(//prebuilts/studio/sdk:build-tools/19.0.3)"
```

See also: [bazel filegroup rule](http://bazel.io/docs/be/general.html#filegroup)

### Make sure tests still pass

```
$ cd /path/to/studio-master-dev/

# Remove all ignored files, since sdk.gitignore strips many out
$ repo forall \
    prebuilts/studio/sdk/darwin \
    prebuilts/studio/sdk/linux \
    prebuilts/studio/sdk/windows \
    -c git clean -dxf

$ bazel test ...
```

### Upload code review

```
$ cd /path/to/studio-master-dev/ # or studio-master-dev-sdk

$ repo forall \
    prebuilts/studio/sdk/darwin \
    prebuilts/studio/sdk/linux \
    prebuilts/studio/sdk/windows \
    tools/base \
    -c git commit -a -m "Updated SDK with build-tools;19.0.3"

# Include -t to ensure all code reviews have the same topic. Be careful that
# someone else isn't using the same topic as you at the same time!
$ repo upload --cbr -t

# This opens an editor. Uncomment all SDK branches and save.
```

### Cleanup

Once your CLs are submitted, you can delete local data if you want to.

```
$ cd /path/to/studio-master-dev/
$ rm .repo/local_manifests/all_sdks_manifest.xml

# Delete two of the three SDKs
$ rm -rf prebuilts/studio/sdk/darwin # unless you're darwin
$ rm -rf prebuilts/studio/sdk/linux # unless you're linux
$ rm -rf prebuilts/studio/sdk/windows # unless you're windows
```
