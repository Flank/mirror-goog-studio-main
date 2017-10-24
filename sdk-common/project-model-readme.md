# Model

A typical model would be a tree that looks like this:

- AndroidModel (MyAppProject Module)
  - AndroidProject (MyAppProject)
    - Variant (demoRelease)
      - Artifact (app)
        - resolved
          - Config (merged & resolved)
            - Dependencies (resolved)
        - configs
          - Config (main)
            - Dependencies (main)
          - Config (demo)
            - Dependencies (demo)
          - Config (release)
            - Dependencies (release)
      - Artifact (tests)
    - Variant (demoDebug)
    - ...
    - BuildMatrix
      - dimensions
        - BuildDimension (flavor=demo,full)
        - BuildDimension (buildType=release,debug)
      - Config (main, filter:all/all)
      - Config (demo, filter:demo/all)
      - Config (full, filter:full/all)
      - Config (release, filter:all/release)
      - Config (debug, filter:all/debug)

The build system output is used to construct one or more AndroidModel instances, which are
the root of the model. Each AndroidModel instance contains one or more AndroidProjects. In
the case of Gradle there would always be one AndroidProject per AndroidModel and in the case
of Blaze there would be many.

Projects contain any number of Variants. Variants contain any number of Artifacts, and
the artifact contain the majority of metadata for the project (application ID, etc.).
Artifacts also crossreference a list of Configs, which serve a similar purpose to
ProjectFlavorContainer -- they contain metadata and a list of pathnames that make up
the source folders.

## Use in the IDE

The IDE attaches exactly one AndroidModel instance to each module.

If the IDE wants to use the notion of a _selected variant_, it is the responsibility
of the IDE to implement this concept. It is not represented in the model, which treats
all Variants as equivalent.

## Paths

Paths are represented using PathString objects. PathStrings are lightweight identifiers
that can identify any io.File, nio.Path, or VirtualFile object that would be used
by Android Studio. Unlike Path and VirtualFile, it doesn't depend on an actual
filesystem implementation.

Typically, the paths provided by a real build system will all be files on the local
filesystem that can be converted back to io.File. However, by using a more general path
identifier in the model, unit tests can construct models in their choice of virtual
filesystem. This generalization would also potentially allow us to refer to files
within jars or in other custom filesystems.

In most cases, the paths provided by the model are just the places where the build system
will look for an input file or write an output file. The fact that a path appears in the
model does not guarantee that there is anything on the filesystem in that location, and
clients must check for the file's existence if they want such guarantees.  

# Build system to Gradle mappings

Gradle creates one Module for each leaf Gradle project. Each Module has an AndroidModel
containing a single AndroidProject (that is, the projects list in AndroidModel always
has exactly one element).

## Gradle AndroidProject

There is a 1:1 mapping from Gradle AndroidProject instances and projectmodel AndroidProject
instances.

Projectmodel doesn't expose the Gradle model version number. All features of different
versions of the Gradle model are normalized during the conversion to projectmodel, and
any feature that is missing from a specific Gradle model version will be exposed in
projectmodel as a missing optional feature. 

The project name and project type attributes map fairly directly from Gradle and
projectmodel. 

## Build Matrix

Note: The build matrix is a planned future addition to the model. It is not
implemented yet.

BuildMatrix describes all the build types and flavors present in the project. It
also describes the default configuration and all the source providers, including
the main source provider and any per-artifact source providers. 

Gradle projects always fill in the optional buildMatrix attribute on AndroidProject.
The first dimensions of the build matrix correspond to flavor dimensions, in the 
same order dimensions are declared in the build.gradle file. The last dimension
corresponds to the build type.

Cells in the BuildMatrix correspond to variants of a specific artifact. They
are identified using an ArtifactVariantPath. ArtifactVariantPath are a lot
like Gradle variant names, except:

- ArtifactVariantPaths stored as lists of strings as opposed to the single
  camelCase string used by Gradle variant names.
- ArtifactVariantPaths have an extra segment which is the name of the Artifact.

For example, the variant path pro/release/main would identify the proRelease 
variant's "main" artifact (where proRelease is the variant that uses the 
pro flavor and release build type).

Each configuration (build type, flavor) and source provider pair is combined into
an instance of Config. The Config describes all the source folders
involved in that build type/flavor along with any configuration metadata it overrides.

Configs are inserted into the build matrix with an ArtifactVariantFilter.
The filter identifies which variants and/or artifacts the Config should
be used with. So from the perspective of a model consumer, a build type or flavor
would both be instances of Config. The only difference is what sort of 
filter is attached.

ArtifactVariantFilters are essentially like ArtifactVariantPaths except that they
permit nulls, which are interpreted as "match everything in this segment" wildcards.

### Build Types

Build types are represented in the BuildMatrix as a Config with a filter
that only matches the second-last segment of the ArtifactVariantPath. So, for example,
the "debug" build type in a 2-dimensional build matrix would use the filter
null/debug/null. The first null means that it applies to all flavors. The
"debug" segment indicates that it applies to the debug build type, and the final
null indicates that it applies to all Artifacts from that build type.

### Flavors

Flavors are also represented using Configs, except 
that they use a filter that matches an earlier segment of the
ArtifactVariantPath.

Flavors also differ from build types in that their Config will never
override certain attributes - such as isDebuggable. Configs return
null from such attributes, indicating that they don't modify that attribute.

### Flavor Combinations

Gradle permits custom configuration to be attached to specific flavor combinations.
Such flavor combinations are also represented as Configs but
they use a filter that matches multiple segments. For example, the following
filter would match only match the v2 and pro flavors:

v2/pro/null/null

### Default Configuration

The default configuration for a project (the "main" source tree) is attached
to the BuildMatrix with a filter that matches everything. It is always the
first Config in the matrix, indicating that everything else overrides it.

### Per-artifact Configuration

Configuration and source files that only apply to a single artifact also
use Configs, but they use a filter that only matches the last
segment. For example, this filter would match all variants of the "main"
artifact:

null/null/main

### Flavor/build type Priorities

Gradle inserts Configs into the BuildMatrix in priority order.
That is, configs inserted later will override identical attributes in earlier
configs.

## Artifacts

Gradle artifacts map fairly directly onto the projectmodel Artifact class.
However, the way API consumers access the artifact's metadata differs. In gradle,
the AndroidArtifact class has a bunch of methods that return metadata
(such as getApplicationId()).

Artifacts in projectmodel store the result of merging their configs
(the main config, flavors, build type, and any per-artifact configuration)
in a Config called "resolved". Application code that wants to know what
went into the artifact but doesn't care what flavor it came from will normally
use Artifact.resolved rather than examining the build matrix.

So, for example, to access the application ID using a projectmodel Artifact,
a caller would invoke artifact.source.overrides.applicationId.

In addition to the _resolved_ config, each artifact has cross-references to
the constituent configs that went into the artifact, in priority order. So - for
example - a typical artifact might reference the config for the main source provider,
one for the build type, one for the flavor, and one for a variant-specific override.
 
## Variants

Gradle always populates the optional variantPath attribute of Variant.
The last segment of the variantPath is the build type, and the preceeding
segments correspond to flavors.

There is no _merged flavor_ associated with Variants in projectmodel.
API clients should use the _source_ from one of the artifacts instead.
(Doing so is more accurate than using the Variant info since it includes
any per-artifact configuration and overrides).

## Dependencies

Gradle will not fill in the optional dependency information for individual
configs in the build matrix (since that information is not reported by the
Gradle model), but it will fill in the resolved dependencies for each Artifact.

# Blaze

This section describes the mappings from blaze BUILD files to the projectmodel
objects.

Blaze only creates one Module. It will have an AndroidModel containing many
AndroidProject instances.

Each android_binary rule will become an AndroidProject of the APP type. Each
android_library rule will become an AndroidProject of the LIBRARY type. Each
android_manifest_merge rule will become a Config containing only a
list of manifest files. Each android_resources file will become a
Config containing only a list of resources. android_test and
android_robolectric_test targets will also become AndroidProjects of type TEST. 

The AndroidProject rules created by Blaze will have one Variant containing one
Artifact. There may be multiple Configs attached to each Artifact,
one containing all the source included in the blaze rule, along with separate
Configs for the android_resources and android_manifest_merge rules that
are referenced by the original rule. The name of the Configs will
match the name of the blaze rule they came from.

Blaze will not supply a build matrix for its models.

Blaze will not fill in the dependencies for Configs that come from
a android_manifest_merge rule or android_resources rule, but will fill them in
for resolved configs and for the configs that come directly from the rules
that produced AndroidProject instances.

Note that - unlike gradle models where each Config contains no more than
one manifest - it will be common for Blaze models to contain multiple manifests
per fragment.

# Design principles

This section describes the general design principles that guided the design of the
model as a whole.

## Modelling Criteria

Features are added to the model as they are justified by use-cases in the
build-system-independent tooling. The model is not required to be sufficiently
complete to build the project, so some information known to the build system will
not be exposed in the model.

The presence of features that aren't supported by all build systems or that aren't present
in all projects will be exposed explicitly via capabilities. API consumers are
required to check before accessing that feature. For example, the presence of a
capability might be indicated by a nullable field.

## Coding Style

The entire model is invariant. The build system is required to replace the model's
root when it changes, using shallow copies at its option.

Kotlin data objects are preferred for all data types. If portions of the model 
are found to be inefficient to construct for large models, those portions of the model
can be lazily constructed provided the model types remain externally invariant and
implement correct value-based hashCode and equals implementations. 
