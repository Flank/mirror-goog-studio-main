# Checkout and build the source code

Like the Android operating system, Android Studio is open source and free of charge to all.
Android releases source code to the Android Open Source Project (AOSP) after each stable release,
described in detail here.

As of Android Studio 1.4, Android Studio is aligned to the same model of releasing source code
after each stable release. For those who contribute to Android Studio,
the code contribution process is the same as the Android platform.

Please continue to submit patches to the Android Studio AOSP branch.

We will do code-reviews and merge changes into subsequent versions of Android Studio.
We're incredibly grateful to all of you in the community for your collaboration and hard work on
Android Studio.

## Doing a checkout

[Download an install the `repo` tool](https://source.android.com/source/downloading.html)
to checkout the source of Android Studio.

Check out the latest published source code using the following commands in a shell:
```
$ mkdir studio-master-dev
$ cd studio-master-dev
$ repo init -u https://android.googlesource.com/platform/manifest -b studio-master-dev
$ repo sync
```
You can call the top level directory whatever you want.
Those of us who check out multiple branches prefer to name the directories after the branches.

During the `repo init` command, it will ask you for your name and e-mail address which
will be used by git if you commit changes and will be shown if you upload them for review.

## Building

To build Android Studio, see http://tools.android.com/build/studio

To build the Android Gradle plugin, see
[Building the Android Gradle Plugin](build-system/README.md).

The parts of the SDK that can be built with the `studio-*` branches are only the IDE components and
the SDK Tools.
Each component is built differently due to varying build systems.

None of them use the make-based build system of the platform.

Historically, building the Android tools required building the full Android SDK as well.
However, we've been gradually migrating the tools source code over to a more independent setup,
and you can now build the Android Studio IDE without a full Android checkout and without a C
compiler etc.

## Check out a specific release

Releases since Android Studio 2.4 are tagged in git. This means you can use the tag to get the source code for a
specific version. The tags are of this form:

 * Gradle: gradle_x.y.z
 * Studio: studio-x.y

You can see all available tags here: https://android.googlesource.com/platform/manifest/+refs

For instance you can do a checkout of version 2.3.0 of the Gradle plugin with the following command:
```
$ repo init -u https://android.googlesource.com/platform/manifest -b gradle_2.3.0
$ repo sync
```

Releases before studio-1.4 were developed in AOSP in the following branches

| development branch | release branch     | IntelliJ       | Notes                            |
|--------------------|--------------------|----------------|----------------------------------|
|studio-1.0-dev      | studio-1.0-release | idea13-dev     | This was the branch for 1.0 work |
|studio-1.1-dev      | studio-1.1-release | idea13-1.1-dev | This was the branch for 1.1 work |
|studio-1.2-dev      | studio-1.2-release | idea14-1.2-dev | This was the branch for 1.2 work |
|studio-1.3-dev      | studio-1.3-release | idea14-1.3-dev | This was the branch for 1.3 work |

Android Studio does not use the `master` branch.
The branches `ub-tools-idea133` and `ub-tools-master` are deprecated.
