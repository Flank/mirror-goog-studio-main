The code in this folder is used to detect changes in the foreground process on the device and send them to Studio.

`foreground_process_tracker.cc` is a library for the Transport Piepeline. It is added as a dependency in `tools/base/transport:transport_main`.

`foreground_process_tracker.cc` is tested in the `test` module by using fakeandroid.