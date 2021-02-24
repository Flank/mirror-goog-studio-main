Classes which sit in a layer on top of the Android framework, showing up
earlier in the classpath, so the compiler will reference them instead of
the framework versions.

These overrides serve on of the following purposes:

* They restore compile-time access to fields and methods normally hidden
  via `@hide` annotations.

* They provide a minimal layer of logic allowing us to run our tests
  without needing to actually run on an Android device.

Note: Fake android is allowed to deviate from the real Android framework
for testing purposes, e.g. to expose ways to modify framework state
directly. Developers should attempt to document these cases, and I have
tried to mark all deviations with @VisibleForTesting for clarity.

Note 2: Fake android should always be written in Java, to ensure that
the byte code generated around it (when called from within the
inspector) matches what would have been generated if compiling against
the actual Android jar.
