# Module sdk-common

This is a utility module that holds code common to all modules that make up the Android
SDK. It is also used by Android command-line tools.

This file is a module description. See go/module-description for schema.

## Justification (why does this module exist?)

This is a place to put code that depends on sdklib but not Android Studio and is used by more
than one Module. It exists primarily to avoid code duplication between modules.

## Inclusion Criteria (what classes belong here?)

Code should only be added here if it is needed by more than one module and there is no more specific
place to put that code.

Self-contained code that does not depend on sdklib or other modules can also go in the "common"
module.

Do not add:
- Any publicly mutable singletons. Create a new module for such code if there is no better place
  to put it.
- Build-system-specific classes. Such code should be moved elsewhere in the dependency graph if
  discovered.

## Dependency Restrictions (what can it depend on?)

Do not add dependencies on:
- Any build-system-dependent modules.
- Any module that contains UI.
