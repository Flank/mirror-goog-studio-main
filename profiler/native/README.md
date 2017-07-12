# Performance tools native

Native (C++) binaries and dependencies used by preformance tools
insfrastructure.

## Prerequisites

* All sections below expect you to start in `.../tools/base/profiler/native`
* You should have `bazel` in your path, and it should point to `.../tools/base/bazel/bazel`

## To compile everything
```
bazel build ...
```

By default, our builds are configured to optimize for build speed, somewhere
between debug and release builds.

## To compile everything for release
```
bazel --config release ...
```

The `release` config is defined in `.../tools/bazel.rc` and automatically
supplies various parameters, such as `-c opt`, to ensure all binaries are
optimized.

## To compile explicitly for debug
```
bazel build -c dbg ...
```

You can use the `-c dbg` option with any of the following sections as well, if
necessary.

## To compile just host binaries
```
bazel build perfa:libperfa.so perfd:perfd
```

## To compile just Android binaries
```
bazel build perfa:android perfd:android
```

## To run the host unit tests
```
bazel test ...
```

## To build a specific ABI

By default and for convenience, our bazel configuration is set up to always
build all Android ABIs. If for some reason you want to restrict the build to a
particular one, edit `.../tools/bazel.rc` and change the following line:

```
build --fat_apk_cpu=x86,armeabi-v7a,arm64-v8a
```

For example, for emulator only:

```
build --fat_apk_cpu=x86
```

Of course, this is for development only. Don't check in such changes.



