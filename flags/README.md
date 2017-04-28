# Android DevTools Flags

A *flag* is a setting that allows the configuration of or gating of some logic
or feature. It can almost be thought of as a simple constant but with some
extra functionality that we will go over in this document.

The most common flags specify whether a feature should simply be enabled or
disabled (useful for devs working on a new feature that's not ready to launch,
for example). Flags can also be used to branch the internal behavior of a
feature (e.g. applying a new but not thoroughly tested algorithm, or altering
the UI to see if the new layout is more intuitive), while still leaving the old
behavior in the code in case a quick fallback is necessary.

**The goal of this library** is to allow defining, enumerating, querying, and
updating a collection of flags. At its simplest, it will allow developers to
define all flags in a standard way in a central location, with an easy-to-read
API.

However, in addition, it will also enable the easy configuration of flags via
command-line arguments and configuration files. The system will even allow for
running experiments, such as having 10% of random users running with a flag set
to one value while the other 90% run with another. Just by defining flags with
this system, these extra features are available with very little additional
effort.

[TOC]

## Defining flags

A codebase should determine a central location where all flags should live.
There, it should create a class whose sole purpose is to contain static flags
and nothing more.

For a concrete example, let's assume we're going to make a game with several
systems, two of which are: *audio* and *graphics*.

And these systems have a few features we'll want to configure: whether to use
3D audio, an initial resolution, and an FPS cap. For these, we'll create the
following flags: `audio.3d`, `graphics.resolution`, and `graphics.fps.cap`.

**Note:** Flag IDs are all lowercase and may only contain letters and numbers.
Spaces are not allowed. Instead, use periods to indicate spacing, as in
`fps.cap` above.

While the program will provide its own defaults for all flags, you might
imagine configuring them via the command-line; for example:

`java ... -Daudio.3d=false -Dgraphics.resolution=640x480`

### Flags Container Class

First, define the shell.

```java
public final class GameFlags {
   private GameFlags() {}
}
```

### Flags

Next, create a `Flags` instance, which you can think of as the owner of all
flags.

```java
public final class GameFlags {
   private static final Flags FLAGS = new Flags();
   private GameFlags() {}
}
```

For these first examples, this class won't do anything; you simply have to
define it since the next section requires it. However, for later, you can think
of this class as the only mutable part of the flag API. Whereas everything else
defines fixed values, `Flags` provides a mechanism for overriding them,
allowing user customization and A/B testing experiments.

### Flag Groups

`FlagGroup`s allow each system to define their own scoped flags without
worrying about conflicting with any other flags:

```java
public final class GameFlags {
   private static final Flags FLAGS = new Flags();

   private static final FlagGroup AUDIO = new FlagGroup(FLAGS, "audio", "Audio");
   private static final FlagGroup GRAPHICS =
      new FlagGroup(FLAGS, "graphics", "Graphics");
   
   private GameFlags() {}
}
```

By specifying the group names as we did above, any flags created within these
groups will look like *audio.**xxx*** and *graphics.**xxx***. You also have to
specify a display name for the group, which should be easy to understand if
shown to users.

### Flag

At last, let's specify some flags.

Flags support common primitive value types: *int*, *bool*, and *string*. Use
the appropriate `Flag#create(group, ..., defaultValue)` methods to create them.

```java
public final class GameFlags {
   ...

   public static final Flag<Boolean> USE_3D_AUDIO = Flag.create(
      AUDIO, "3d", "Enable 3D audio", "... description ...", true);

   public static final Flag<String> RESOLUTION = Flag.create(
      GRAPHICS, "resolution", "Initial resolution", "... description ...",
      "1280x720");
   public static final Flag<Integer> FPS_CAP = Flag.create(
      GRAPHICS, "fps.cap", "FPS cap", "... description ...", 30);
   ...
}
```

The name passed into `create` will be prefixed by the `FlagGroup`'s name,
e.g. `3d` above becomes `audio.3d`.

And that's it! Now that these flags are defined, they can be used in code:

```java
if (GameFlags.USE_3D_AUDIO.get()) {
   ... 3D audio logic ...
}

Dimension resolution = Dimension.parse(GameFlags.RESOLUTION.get());

int currFps = update(...);
if (currFps > GameFlags.FPS_CAP.get()) {
   ... sleep ...
}
```

## Overriding Flags

A flag which cannot be overridden is no better than a constant. This section
discusses the various ways you can override a flag.

### FlagOverrides

The `FlagOverrides` class represents a collection of flag-to-value mappings,
where if a value exists it should act as an override to its associated flag's
default value.

A `Flags` instance will always contain a single, mutable `FlagOverrides`
instance, plus 0 or more fallback instances. Since a mutable collection always
exists, this ensures a user can always override any flag manually.

A concrete example can help, here. Say you have some values that are read in
from the command line, others pulled down from a server, and finally others
chosen by a user in some "Edit Settings" UI. In this example, the user's
settings should be respected first, followed by the remote configuration, followed by the
command-line values.

You would specify this by constructing `Flags` like:

```java
Flags flags = new Flags(userSettings, remoteSettings, commandLineSettings);
```

The first argument must always be the mutable collection, and it will always be
checked first; the remaining arguments are checked in the order specified.

**Note:** If you do not explicitly pass in a mutable `FlagOverrides` instance
as the first argument, `Flags` will automatically create one. The mutable
`FlagOverrides` instance can be accessed via `Flags#getOverrides()`.

### Java System Properties

To use Java System properties as a source of flag overrides, use the provided
`PropertyOverrides` class.

```java
Flags FLAGS = new Flags(new PropertyOverrides());
```

This class reads in all System properties and treats them as potential flags
overrides. Most system properties are just noise and will never be used, but if
a property name matches the IDs of a flag, its value will be used as an
override.

For example, if you start an application like so:

`java ... -Dgraphics.fps.cap=60`

then, even if you define a flag with a default value of 30:

```java
public static final Flag<Integer> FPS_CAP = Flag.create(
   GRAPHICS, ..., 30);
```

`get()` will return the overridden value, 60:

```java
assertThat(System.getProperty("graphics.fps.cap")).equals("60");
assertThat(FPS_CAP.get()).isEqualTo(60);
```

### Flag API

Although you can technically override a flag directly through the
`FlagOverrides` instance returned by `Flags#getOverrides()`, in practice, you
often won't have access to the parent `Flags` class (which should be declared
`private`).

The recommended way to override a flag's value is through the `Flag#override`
method. Note that this call does not actually modify the flag itself but rather
updates the mutable `FlagOverrides` collection in its parent `Flags` class for
you. Convenient!

```java
GameFlags.FPS_CAP.override(45);
// Same as: GameFlags.FLAGS.getOverrides().put("graphics.fps.cap", "45");
```

Besides being easier to read, this API has the additional advantage of ensuring
type-safety. `Flag#override` won't let you set a flag's override value to an
incompatible `String` value by mistake, like this typo for example:
`FLAGS.getOverrides().put("graphics.fps.cap", "45'")`

**Note:** Overriding a flag is not thread safe, so you must be careful if you
are overriding flags in one thread while reading their values in another.

### Serialization

If you'd like to persist a user's flag settings across multiple sessions, all
you need to do is save the values in `Flags#getOverrides()` to disk on exiting
and restore them on load.

There are many libraries and approaches on serializing data, but the skeleton
of a simple example is provided for concreteness:

```java
// On closing your application...
List<String> flagValues = new ArrayList<>();
List<Flag<?>> flags = ... // Use reflection to get all flags?
for (Flag<?> flag : flags) {
  String value = FLAGS.getUserOverrides().get(flag);
  if (value != null) {
    flagValues.add(String.format("%s=%s", flag.getId(), value));
  }
}
// Write flagValues to file
```

```java
// On starting up your application...
List<String> flagValuesLines = ... // load from disk
Map<String, String> flagValues = new HashMap<>();
for (line in flagValuesLines) {
    String[] split = line.split('=', 2);
    flagValues.put(split[0], split[1]);
}

// Flags.getOverrides() will be empty on startup
List<Flag<?>> flags = ... // Use reflection to get all flags?
for (Flag<?> flag : flags) {
    String value = flagValues.get(flag.getId());
    if (value != null) {
       FLAGS.getUserOverrides().put(flag, value);
    }
}
```

*More robust validation and error handling is left as an exercise to the
reader.*

You might also create your own custom `FlagOverrides` implementation class and
pass that into your `Flags` constructor:

```java
class PersistedOverrides implements FlagOverrides {
    private Map<String, String> myOverrides = new HashMap<>();
    public PersistedOverrides() { deserialize(); }
    public void serialize() { ... }
    private void deserialize() { ... }
}
...
Flags FLAGS = new Flags(new PersistedOverrides(), ...);
```

<!--
  Continue updating README as new features are developed.

  TODO: Experiment framework
-->

