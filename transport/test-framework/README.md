# Transport Test Framework

The transport test framework consists of a library, a mock app, and bazel
rules, which allow you to write integration tests to verify logic built on top
of the transport API to communicate between host and device.

The framework provides support for spawning a fake Android device on your
machine, launching activities on it, and interacting with those activities.

A common pattern for a single test is to launch an activity, trigger a function
in its public API, have that function log some text, and then verify that you
see the text on the host side. You can also initiate a gRPC call and then wait
for the expected reply. Test utilities are provided to make these sorts
of tasks easier.

A demo test, `SimpleTransportTest`, is provided to showcase these approaches.

## Relevant Components

### fakeandroid

For more information, see `//tools/base/fakeandroid/README`

### test-app

A minimal app skeleton / mock Android app. Perhaps most importantly it provides
a `TransportTestActivity` base class. All transport tests must subclass it,
as it contains some initialization logic that allows these tests to work.

Other areas of the codebase will likely create their own test apps, using this
app as a dependency.

### test-framework

The various classes needed for setting up and running a transport test.

`TransportRule` is a particularly useful class - it is a JUnit4 rule which
acts as your central hub for spawning and interacting with a target process on
the fake Android device.

#### Properties

The test framework requires a handful of properties be set to work appropriately.
See `Constants.kt` for the full list.

If you are running the test through the `transport_test` rule found in the
`transport_test.bzl` file, this will be handled for you.

## Writing a test

*Note: For now, all transport test apps must be written in Java. This is because
both the rule for bundling test apps AND the rule for launching a fake
android device don't support Kotlin at this time.*

When writing a new test, you'll often create two classes -- an `Activity` class
and a test class.

### Mock app module

In some mock app module, the skeleton for your activity class will look like
this:

```
@SuppressWarnings("unused") // Class accessed by reflection
public final class DemoActivity extends TransportTestActivity {
    public SimpleActivity() { super("DemoActivity"); }

    ... one or more public void methods ...
    public void doSomething() { ... }
}
```

### Test module

In a separate test module, you'll write a test that depends on the
`transport-test-framework` library, which will look something like this:

```
public final class DemoTransportTest {
    public static final String ACTIVITY_CLASS = "com.activity.DemoActivity";

    @Rule
    public TransportRule transportRule =
        new TransportRule(ACTIVITY_CLASS, SdkLevel.O);

    @Test
    public void verifyDemoBehaviorHere() {
        transportRule.getAndroidDriver()
            .triggerMethod(ACTIVITY_CLASS, "doSomething")
        ...
    }
}
```

Of course, writing the test logic after triggering some initial method is where
all the work happens. The test framework provides a few tools to help here,
which we'll go over next.

#### TransportRule#getAndroidDriver

The transport rule exposes an Android driver. This is a class which lets you
send useful commands to the device. Triggering a method is often how you'll
kickstart a test.

#### TransportRule#getGrpc

The transport rule exposes a gRPC connection, and
`getGrpc().getChannel()` is particularly useful for tests because that
can be used to instantiate various API stubs. For example:

```
TransportServiceGrpc.newBlockingStub(transportRule.getGrpc().getChannel())
```

#### StubWrappers

Often, you'll find many common test operations don't exist (or belong) in the
underlying transport APIs, e.g. asserting that some command results in an
expected number of matching events getting generated.

The common pattern we use in this framework for such extensions is by providing
stub wrappers. These classes wrap an underlying gRPC service and provide
extended functionality.

You are encouraged to adopt this pattern in your own codebase, if you are
testing against new gRPC APIs not covered by this framework.

#### TestUtils

TestUtils own miscellaneous, static utility methods which can help simplify
common operations, such as waiting infinitely for something to get logged that
you know will happen eventually.