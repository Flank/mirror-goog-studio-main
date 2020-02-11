Lint Model
===========

---------------------------------------------------------------
**NOTE**: The lint model API is not yet stable; it is
in active development. There is a TODO list at the end of
this document which lists some of the planned work.
---------------------------------------------------------------

This library provides a build-system-agnostic view of the project
layout, to be consumed by lint. It does not give a complete
description of the project; it only contains information that
lint cares about. For example, while the Gradle builder-model
provides information such as the `applicationId` of a given
variant, the lint model does not contain that since it is not
used by any lint checks.

The current shape of the API is heavily influenced by the
builder-model library for the Android Gradle plugin: it contains
concepts such as variants, source providers, artifacts, manifest
place holders, and so on -- though it has removed a number of
concepts such as product flavors and build types; these
attributes from these have been merged and folded into the
variants directly.

TODO
----
* Hook persistence up to AGP.
* Add DependencyGraph separation. Unit test should have their
   own graph.
* Create a project model which references the various lint
      models; handles the checkDependencies stuff, and includes
      whole project metadata like global lint rules, target
      platform (android vs jdk vs studio etc)
* Should the model have a build id?
* Add support for lint.jars not just at the library level
* Add a plugin for Gradle (depending on older versions, such
      as 3.6) which can spit out the XML model. That way you
      can point to it from a separate lint task (4.1).
* getTestSourceProviders() in LmVariant needs to not combine
  instrumentation and unit tests, as described in the javadoc.
* LintCliClient#addBootClassPath should use the bootclasspath
  from the lint model
* Investigate Kotlin-only source sets and make sure they're
  correctly included in IDE projects!
