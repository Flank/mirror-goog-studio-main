## Lint Classloader Isolation

tl;dr: The Gradle plugin will no longer depend directly on lint, and
when lint runs as part of the Gradle plugin it places all of its
dependencies (those not shared with the Gradle plugin) in its own,
isolated class loader. This lets lint use different versions of
libraries (such as the Kotlin compiler) than the ones used by other
Gradle plugins (such as the Kotlin compiler used in the Gradle Kotlin
plugin.)


### Problems with Old Architecture

Until now, the Gradle plugin depended **directly** on lint and all its
dependencies. Gradle doesn't seem to have a plugin/module system like
most IDEs. This means that when two plugins use incompatible versions
of a single library, Bad Stuff™ can happen. Until now though, that's
generally only been an issue with third party plugins that were ahead
or behind one of the libraries we were using, and generally those
plugins would quickly adapt to something compatible such that they
work for Android.

However, in 3.1, I'm adding support for analyzing Kotlin from
lint. This means including the Kotlin compiler as a dependency. Kotlin
already has an "embeddable" compiler, and that is in fact what is used
in the Gradle Kotlin plugin. However, this compiler packages not just
a subset of the Kotlin PSI support, it also packages a subset of the
intellij-core classes (which lint is already using for Java
analysis). This subset is incompatible with the "real" intellij-core
classes, because it's a proguarded version of intellij-core which only
includes the classes **and class members** that are used by the Kotlin
compiler. In short, lint **cannot** directly reuse the same classes as
the Gradle Kotlin plugin. One possible solution would be for lint to
repackage all of its dependencies (using something like jarjar), such
that instead of referencing com.intellij.psi.PsiElement, it would
ferences com.android.tools.lint.com.intellij.psi.PsiElement. This
would work from within the Gradle plugin, but it has two critical
flaws which makes it a non-starter: First, lint has to use the real
package names because the same code is also run directly within the
IDE (accessing the IDE's warm data structures), and second these
classes are part of lint's public API (UAST is the parse tree library
it's using, and UAST is partially based on (and exposes) PSI.)


### New Architecture

In 3.1 we now have two new modules:

* **lint-gradle**: This module contains most of the lint code that
     used to live in the Gradle plugin, such as the code to create a
     lint project graph from the Gradle project. This module depends
     on both lint and Gradle APIs.

* **lint-gradle-api**: This module has no dependency on lint, or on
     the Gradle plugin. It defines a simple interface to invoke lint
     (you populate a data object with parameters lint will need, such
     as which Gradle project to analyze, where to place the report
     files, etc.)

The Gradle plugin now **only** depends on lint-gradle-api. The
existing tasks, such as LintGlobalTask (the task for the "lint" task)
and LintPerVariantTask (for tasks like "lintDebug" and "lintRelease")
now just call into the simple lint-gradle-api to invoke lint analysis.

This isn't just used for lint; there were several other tasks in the
Gradle plugin that were calling into lint services:

* Annotation extraction. This also depends on most of lint's
    dependencies (PSI, UAST, etc), so I made a parallel API in
    lint-gradle-api for annotation extraction, and it uses the same
    class loader to call into the annotations handling code.

* Resource shrinking. This was using lint's resource model (shared
    with the unused resource detector for example). I just moved that
    one out to sdk-common.


### Class Loader

The lint-gradle-api constructs a class loader to run lint in.

This class loader is **not** parented by the current class loader,
since that class loader may already have conflicting classes in it,
like the Kotlin PSI classes from the embedded Kotlin compiler from the
Kotlin Gradle plugin.

However, we can't just make a **completely** isolated class loader for
lint, since we're passing various objects into it (such as Gradle
projects, builder-model objects like Variant, sdklib objects like
BuildInfo, and so on). If were to load the Gradle, sdklib and
builder-model libraries anew in our ClassLoader, then objects passed
in from Gradle would be inconvertible.

Therefore, the ClassLoader has to very carefully curate its
dependencies:

*   Start with all the libraries lint depends on, transitively, and then
*   Skip any library that the Gradle plugin already depends on

However, as already mentioned we don't parent the lint class loader by
the Gradle class loader (if we did, then any classes already loaded by
that loader would be used before our class loader is even
consulted). That means that if code loaded in lint attempts to
reference one of the above Gradle APIs, they won't be found, since
they're not part of this class loader.

Instead, we use the Gradle class loader as a **delegate** instead of a
parent. When we attempt to load a class, we first try in our URL class
loader (pointing to all the lint dependencies minus Gradle
dependencies). And only if it's not found there, we then delegate the
load class to the gradle class loader.

We have to do the same thing for resource loading, since for example
the Kotlin runtime machinery attempts to load services, and the
service loader machinery uses findResource() rather than findClass().


### Class Loader Construction

The class loader constructed by lint-gradle-api and where lint-gradle
is loaded and excuted, is a URLClassLoader. It is constructed from the
dependencies of lint-gradle.

The full set of dependencies is computed by Gradle itself. The trick
is that the base plugin creates a special "lint class path
configuration" which depends on the group:artifact for the lint-gradle
library:

```java
Configuration config = project.getConfigurations().create(LintBaseTask.*LINT_CLASS_PATH*);
config.setVisible(false);
config.setTransitive(true);
config.setCanBeConsumed(false);
config.setDescription("The lint embedded classpath");
project.getDependencies().add(config.getName(), "com.android.tools.lint:lint-gradle:" +
       Version.ANDROID_TOOLS_BASE_VERSION);
```


When lint runs it can look up this dependency as a file collection:

```java
task.lintClassPath = variantScope.getGlobalScope().getProject().getConfigurations()
       .getByName(LintBaseTask.*LINT_CLASS_PATH*);
```

And from the files it computes URLs that it passes to the class loader.

Note however that the above will include all the libraries that lint
depends on, including all the ones Gradle depends on, and that's the
case for most of them. Currently we only need 11 libraries for lint,
and the full dependency list above contains over 60 jars.

Lint tries two different ways to compute the class path:

* First, it looks up the full class path required by gradle-lint.

* Then, it looks up the current (Gradle) class loader, and if it's a
    URL class loader, then it subtracts out all the URLs found in the
    Gradle class loader from the Lint class loader.

* It does not compare URLs (because that would involve network access
    - URL.equals is documented to be blocking), and it does not
    compare URIs because sometimes the lint class path and the gradle
    plugin class path find the same libraries in different places
    (e.g. builder-model-3.1.0 comes from the ~/.gradle cache in one
    case and from the lint class path maven repo in the other case),
    so we compare library names.

* At the end, we perform a validity check (making sure we **did** find
    the lint jars and that we **didn't** include a jar we know should
    be excluded (builder-model)), and if all is well we use the URLs
    in this delta list.

* If something goes wrong in any of the above, we fall back to a
    "hardcoded" list, similar to what is in build.gradle, of the known
    dependencies for lint.


### Class Loader Lifecycle

There is only one Lint class loader created by ReflectiveLintRunner,
and it exists for the entire lifetime of the Gradle daemon.

Parallel invocations of Lint also share the same LintCoreApplicationEnvironment
(in order to share caches). However, we don't want the Gradle daemon to hang on
to PSI machinery forever. So, we dispose the LintCoreApplicationEnvironment
at the end of the build via a BuildCompletionListener (which will be invoked when
**all** projects are done, not after each one has been built). Lint code must still
be diligent to avoid leaking memory via static fields.

Historical note: we used to destroy the Lint class loader after each
build invocation. However, the class loaders were leaked by intellij-core code
in various ways (JNI globals, thread-local variables), so in practice the class
loaders were never GC'd. Plus, Lint invocations in a warm Gradle daemon were
slowed down by having to reload classes again. We avoid both of these issues by
just holding on to a single Lint class loader forever.


### Complications

With this change, lint libraries are no longer fetched from the root
build repositories. This has the advantage that it only **downloads**
and configures these libraries if lint targets are actually invoked.

qHowever, it does mean that you need to ensure that you have
maven.google.com in the build scripts available from all your
projects.  This is not a problem for recently created projects
(e.g. in 3.0), but for older projects, this may not be the case.

If not, you might get an error like this:

```
FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':lib2:lint'.
> Could not resolve all files for configuration ':lib2:_lintClassPath'.
   > Could not find com.android.tools.lint:lint-gradle:26.1.0-dev.
     Searched in the following locations:
         file:extras/m2repository/com/android/tools/lint/lint-gradle/26.1.0-dev/lint-gradle-26.1.0-dev.pom
         file:extras/m2repository/com/android/tools/lint/lint-gradle/26.1.0-dev/lint-gradle-26.1.0-dev.jar
         file:extras/google/m2repository/com/android/tools/lint/lint-gradle/26.1.0-dev/lint-gradle-26.1.0-dev.pom
         file:extras/google/m2repository/com/android/tools/lint/lint-gradle/26.1.0-dev/lint-gradle-26.1.0-dev.jar
         file:extras/android/m2repository/com/android/tools/lint/lint-gradle/26.1.0-dev/lint-gradle-26.1.0-dev.pom
         file:extras/android/m2repository/com/android/tools/lint/lint-gradle/26.1.0-dev/lint-gradle-26.1.0-dev.jar
     Required by:
         project :lib2

* Try:
Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.

* Get more help at https://help.gradle.org

BUILD FAILED in 15s
158 actionable tasks: 158 executed
```

The solution is simple: in your root build.gradle file, ensure that
you have this:


```groovy
allprojects {
    repositories {
        google()
        jcenter()
    }
}
