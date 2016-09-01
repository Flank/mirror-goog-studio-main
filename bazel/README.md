# Bazel utilities

This package contains code related to our Bazel setup: support for compiling IntelliJ
forms, Kotlin, custom macros, BUILD files generators etc.

## BUILD files generators

There are three `java_binaries` here that can be used to manage dependecies in
prebuilts. Please note that these tools don't format the BUILD files, so you
should run `buildifer` over the newly created files afterwards.

### `third_party_build_generator`

Used to generate `//tools/base/third_party/BUILD`. Computes effective versions of
all necessary dependencies and creates a `java_library` rule for each one of
them. It will also download missing jars into `//prebuilts/tools/common/m2/repository`.

Can be invoked by running `bazel run //tools/base/bazel:third_party_build_generator`.

The dependencies we need are specified in the BUILD file in this package.

### `add_dependency`

Can be used to download one or more Maven artifacts into prebuilts, including
all transitive dependencies.
 
Invoked by running
`bazel run //tools/base/bazel:add_dependency com.example:foo:1.0`.

You can also use it to download protoc binaries, like this:

`bazel run //tools/base/bazel:add_dependency com.google.protobuf:protoc:exe:linux-x86_64:3.0.0`

### `java_import_generator`

Creates a BUILD file for every POM file in the prebuilts maven repo. Both of
the binaries above do the same, but this can be useful if prebuilts was
modified using Gradle's `cloneArtifacts` tasks or manually.

Invoked by running
`bazel run //tools/base/bazel:java_import_generator`.
