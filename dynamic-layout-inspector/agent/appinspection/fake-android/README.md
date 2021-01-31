Classes which sit in a layer on top of the Android framework, showing up
earlier in the classpath, so the compiler will reference them instead of the
framework versions.

These overrides serve on of the following purposes:

* They restore compile-time access to fields and methods normally hidden via
  `@hide` annotations.

* They provide a minimal layer of logic allowing us to run our tests without
  needing to actually run on an Android device.

