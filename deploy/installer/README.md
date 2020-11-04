# Apply Changes Data Pipeline

Data flow is based on sequential request/response originating from a JVM process running on the user machine.
All "wires" use the "Message" format.

## InstallerD

Requests are always sent over ADB Server to the device on which runs the "Installerd". Once on the device
the request may be fullfitted entirely (Dump request) or it may need additional messages to be exchanged
between InstallerD and the target app being swapped.

## AppServer Deamon (a.k.a InstallServer)

Since the Installerd and the app process run on different uids, they cannot communicate directly. The AppServer
is started via run-as and act as a bridge between the app processes and Installerd.

Full-duplex comm between the two process is maintained thanks to pipes opened when installerd fork(3).
The pipes are connected to the AppServer stdout and stdin to receive requets and send responses.

We call this a deamon because the appServer remains alive between installerd request. This is done to save spawning a
new process on each request. The "natural state" of the AppServer is to be blocked on a read(), awaiting for a request
from the installerd. At any time, there is a maximum of one alive appServerd per applicationID.

Each appserverd monitors its parent process (installerd) and terminates when its parent terminates.
Additionally, appserverd monitors framework re-install via a canary file. If the canary stops tweeting, appserverd also
terminates.

## Agent(JVMTI)

Upon attachment, the agent open a connection on the AppServer named socket to retrieve the request. Upon
completion, it writes its response to the same socket. Respones from all processes are aggregated by the
App Server and sent back to InstallerD.

# Messages format

All messages exchanged use an 8-byte header (0xAC_A5_AC_A5_AC_A5_AC_A5) followed by a little-endian 4-byte
signed size. The payload is a protobuffer stream.
## Recap

```
+---------------------+------------------------------------------------------------+
|      PC/Mac         |                    Android Device                          |
+---------------------+------------------------------------------------------------+
|                     |       Shell user          |            App user            |
| +----------+        |     +-------------+       +                                |
| |Android   |        |     |InstallerD   |       |       +----------------+       |
| |Studio    +-------ADB----+             +-----run-as----+App Server      |       |
| |          |        |     |             |       |       |                |       |
| +----------+        |     +-------------+       |       +---Named Socket-+       |
|                     |                           |              |                 |
|                     |                           |              +-+------------+  |
|                     |                           |                |App (JVMTI) |  |
|                     |                           |                +------------+  |
+----------------------------------------------------------------------------------+
```
