# DDMLib internals

This is the document to read if you are new to ddmlib source code and want to make a change. The intent is to boost what can be achieved within a "window of naive interest". You will not find function or class documentation here but rather the "big picture" which should allow you to build a mental map to help navigate the code.

## Monitoring architecture and Threading model

DDMLib monitors devices state, debuggable app process, and JDWP messages (Standard debugger and Android JDWP extensions as defined in `dalvik/docs/debugmon.html` (Dalvik VM Debug Monitor). The overall system relies on three Threads exchanging information in a waterfall where Thread 1 impacts Thread 2 selector S2 which in turns impacts Thread 3 selector S3.

```
                   DDMLib                          |         ADB Server         |                Devices
----------------------------------------------------------------------------------------------------------------------
                                                   |                            |
                                                   |                            |
DeviceListMonitorTask  (Thread 1)------------------> host:track-devices----+-----------------------------Device B
                  |                                |                       |    |                           |  App Foo
                  +------------->---------+        |                       +-----------------Device A       |    |
                                          |        |                            |              |   App Bar  |    |
DeviceClientMonitorTask (Thread 2)--------S2-+--<--- device:A track-jdwp  -------------<-------+   |        |    |
                  |                          |     |                            |                  |        |    |
                  |                          +--<--- device:B track-jdwp  -------------<-----------)--------+    |
                  |                                |                            |                  |             |
                  +------------->---------+        |                            |                  |             |
                                          |        |                            |                  |             |
MonitorThread (Thread 3)------------------S3-+--<--- device:A jdwp:Bar ----------------<-----------+             |
                                             |     |                            |                                |
                                             +--<--- device:B jdwp:Foo ----------------<-------------------------+
                                                   |                            |
```

1. Thread 1 monitors service "track-devices" outputs form a plain blocking Socket (no Selector here). Based on this data, it adds or remove entries to a Selector "S2".
2. Thread 2 monitors S2. Each entries in S2 are connected to a track-jdwp service socket. Each allow to periodically receive the pids of known debuggable process on a device. Based on the pids returned, it populated Selector S3.
3. Thread 3 monitors S3. Each entries in S3 are connected to a jdwp:pid sercice which carries JDWP traffic. These packets can be either Dalvik VM "Debug Monitor" JDWP extension packets or standard debugger packets.

## Listeners threading model

According to the previous section, notifications have a set Threading model.

- Devices changes (IDeviceChangeListener) notifications occur on Thread 1.
- Client changes, also on IDeviceChangeListener, occur on Thread 2.
- JDWP packet processing is done on Thread 3.

