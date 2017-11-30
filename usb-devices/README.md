# USB Devices

A simple library to detect connected USB Devices.

Currently supported platforms are:
- Mac (through `system_profiler SPUSBDataType`)
- Linux (through `lsusb -v`)

Windows is not supported since there is no easy commandline way to get USB information.

The output is parsed and turned into collection of UsbDevice objects.

