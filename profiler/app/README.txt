This is the root directory for code which deals with modifying or hooking
into the user's app to profile it.

In pre-O devices, we use BCI to modify a user's code at compile time,
attaching supportlib.jar as a dependency. In O+ devices, we use JVMTI to
perform run-time BCI, modifying a user's app as it starts up and binding
hooks to call into perfa.jar.

Code shared between both solutions live under "common/"
