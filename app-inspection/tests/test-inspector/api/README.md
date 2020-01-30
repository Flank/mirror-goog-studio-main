This module generates an API shared between the test inspector and its tests.
That is, tests can use the API to send commands, and the test inspector can use
it to reply with events.

In production, this role will usually be handled by a proto API, but for tests,
this minimal approach is good enough.
