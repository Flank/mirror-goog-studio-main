Represents the model used by C/C++ during sync and build.

The model is:
- immutable
- pure data (no behavior classes)
- acyclic
- normalized (no fields that mean the same thing)
- properly scoped (ex stuff about variants goes on CxxVariantModel only)
- implementation may by lazily constructed
