# Instant Run #

## Objective ##

Android tools to build applications need to provide a better
iterative coding experience. Rather than focusing on rebuilding the entire
application, tools should deliver changes in small increments that can be built,
installed and loaded rapidly. Furthermore, relatively simple code changes should
be delivered in the live process by patching existing loaded code with newer
versions, eliminating the need to restart the applications.

## Overview ##

 * Provide a bytecode manipulation technique to allow swapping new
   implementations of classes without restarting a running Android application
   (called Hotswap).
 * Provide a runtime library to patch and reload updated Android resources
   (icons, layout, etc) while only restarting the Android Activity (called
   Warmswap).
 * For code or Android Manifest changes that cannot be applied to a live
   process, provide a facility to build the minimum delta from the original APK
   to speed up delivery and process restart with the changes (called cold swap).
 * Finally, provide an incremental delivery mechanism based on cold swap to
   deliver changes to a non running process without rebuilding and redeploying a
   full APK.

## Runtime ##

Although the Gradle build-system can function independently of the IDE (it can
compile from Java sources up to packaging the final APK ready to be uploaded on
the Play Store, all from command line invocations) it was decided instant run
would be an integrated experience from the IDE. There is no command line
interface to the features described in this document.

The basic idea of Instant Run is to embed a small server within the application
running on the Android device. This server will open a port to communicate with
the Android Studio instance. User modifies code within the IDE, then press Play.
AS will call the build system to perform an incremental build (based on the
delta of source files). Build system will return the list of artifacts produced
(details in the following paragraphs). Such artifacts will be handed over to the
Instant Run client that use the port opened by the server to push the artifacts.
The server will then be notified what type of restart is necessary (none,
activity, process) and will perform it.

## Verifier ##

When a build is invoked from studio it is not necessarily known what types of
artifacts will be produced. The
[`InstantRunBuildContext`](/build-system/gradle-core/src/main/java/com/android/build/gradle/internal/incremental/InstantRunBuildContext.java)
keeps track of the verifier status, which is updated during the build. At the
end of the build the `InstantRunBuildContext` writes the build info file with
all of the artifacts produced and the verifier status, which studio reads and
deploys the appropriate artifacts.

The verifier can be set in the following ways:
* No instant run state stored gives `INITIAL_BUILD`.
* Injected by Studio: `OptionalCompilationStep.FULL_APK` gives
  `FULL_BUILD_REQUESTED` and `RESTART_ONLY` gives `COLD_SWAP_REQUESTED.`
* `InstantRunVerifierTransform` checks that the classes can be hot swapped.
* `NoChangesVerifierTransform` has two instances, one for java resources
  (`JAVA_RESOURCES_CHANGED`) and the other for dependent projects
  (`DEPENDENCY_CHANGED`).
* Manifest changes gives `MANIFEST_FILE_CHANGED`. A change to the resource IDs
  that get inlined into the compiled manifest file give
  `BINARY_MANIFEST_FILE_CHANGED.`


See
[`InstantRunVerifierStatus`](/build-system/instant-run-instrumentation/src/main/java/com/android/build/gradle/internal/incremental/InstantRunVerifierStatus.java)
for the list of possible states for verification. Depending on the patching
policy
([`InstantRunPatchingPolicy`](instant-run-instrumentation/src/main/java/com/android/build/gradle/internal/incremental/InstantRunPatchingPolicy.java)),
each verifier status maps to an InstantRunBuildMode. For example, a java
resource change results in a full build when using a target device before
Lollipop and multidex but a cold swap for multi-apk. When there are multiple
verifier failures only the first one is stored, but the build modes are
combined. For example `HOT_WARM` and `COLD` gives `COLD`.

## Hot swap ##

### Instrumentation ###

See [Instant run byte code instrumentation](/build-system/instant-run-instrumentation/README.md)

### Hot swap delivery ###

When the build-system determines that all the code changes since the last build
are compatible with the current hot swap implementation, it will create a
reload.dex with all the changed classes instrumented with the incremental
instrumentation. This reload.dex will be used by the Instant Run client library
and transferred to the running application Instant Run server which will write
it on the disk, create a class loader and register the updated classes.

## Cold swap ##

When a code change is outside of the boundaries of the hot swap implementation
(say adding or removing a method for example), we need to restart the
application which is called cold swap. The key here is to do it in the most
efficient manner.

First, we collect all the hot swap changes that may have impacted several source
files since the last APK/Cold swap build and re-create all the slices that are
impacted by the touched source files. If the user touches files in a single
package, since all files belonging to a single java package end up in the same
slice, only one slice will be rebuilt.

The impacted slices are then pushed to the device via the runtime, which writes
them to a known ‘inbox’ location in the app’s data directory. The application is
then restarted. Upon restart, the application will find updated slices in its
incoming mailbox, will overwrite the outdated ones with the new ones and will
then create the application class loader with the updated slices before
delegating back to the user’s application code.

When the application is not running but the user is touching multiple files and
rebuilding. Following the same scenario as cold swap, the changes are accumulated
until the end user finally deploys and run the application. When the deploy
command is invoked, the impacted slices will be first copied over to the inbox
location and the same startup code as for cold swap will be involved to set up
the new class loader. On some devices, run-as is broken, so freeze-swap is not
possible. In this case we fall back to a full build.
