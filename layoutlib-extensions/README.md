# Layoutlib-extensions library

This is a library to be loaded by ModuleClassLoader with a purpose of extending or amending behavior
of user or standard Android framework code. Instead of making changes in layoutlib one could add
code here because because one wants to:
* Use Kotlin (layoutlib currently does not support Kotlin)
* Rely on the code to be loaded by ModuleClassLoader (layoutlib is loaded with intellij plugin classloader)

## Build and update

To build the library and update for the Android Studio run:

```
tools/vendor/google/layoutlib-prebuilt/update_layoutlib_extensions.sh
```

After that, the updated library can be found in ``prebuilts/studio/layoutlib/data/layoutlib-extensions.jar``

