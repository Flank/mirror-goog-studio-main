# Zipflinger

Zipflinger is a library dedicated to ZIP files manipulation. It can create an archive from scratch
but also add/remove entries without decompressing/compressing the whole archive.

The goal of the library is to work as fast as possible (its original purpose is to enable fast
Android APK deployment). The two main features allowing high-speed are Zipflinger's ability to
edit the CD of an archive and its usage of zero-copy transfer when moving entries across archives.

The library is made of four components named ZipArchive, Freestore, Mapper (Input), and Writer
(Output).

```
+------------------------------------+
|              ZipArchive            |
+------------+-----------+-----------+
|  Freestore |  Mapper   |   Writer  |
+------------+-----------+-----------+
                   ^            +
                   |            |
                   +            v
+------------------------------------+
|            MYFILE.ZIP              |
+------------------------------------+
```

Design choice discussion:

Order of operations:
====================
In order to avoid creating holes when editing an archive, zipflinger recommends (but does not enforce)
submitting all delete operations first and then submit add operations. A "deferred add" mechanism was
initially used where delete operations were carried immediately but additions were deferred until the
archive was closed. This approach was ultimately abandoned since it increased the memory footprint
significantly when BytesSource were involved.

Prevent silent overwrite:
=========================
It is by design that Zipflinger throws an exception when attempting to overwrite an entry in an archive.
By asking developer to aknowledge an overwrite by first deleting an entry, this mecanism has allowed to
surface many bugs.

Load entries in memory:
=======================
Zipflinger loads entries in memory before adding them to an archive (unless the entry is coming from an
other archive in which case a zero-copy transfer occurs). This design choice is a trade-off which
increase speed (by allowing multithreaded-compression) and simplify the overall architecture at the
cost of not supporting files bigger than 2GiB.  

## ZipArchive
ZipArchive is the interface to create/read/write an archive. Typically an user will provide the path
to an archive and request operations such as add/delete.

In the code sample below, an Android APK is "incrementally" updated. The AAPT2 output (recognizable
to its file extension .apk_) is opened. Since the archive exists, it will be modified. Had it not
existed, the archive would have been create. Two operations are requested:

1. An old entry is deleted.
2. A new entry is added.

```
 ZipArchive archive = new ZipArchive("app.ap_");

 // Delete (to reduce holes to a minimum, it is mandatory to do all delete
 // operation first).
 archive.delete("classes18.dex");

 // Add sources
 File myFile = new File("/path/to/file");
 BytesSource source = new BytesSource(file, "entryName", Deflater.NO_COMPRESSION);
 archive.add(source);

 // Don't forget to close in order to release the archive fd/handle.
 archive.close();
```

Such an operation can be performed by Zipflinger in under 100 ms with a mid-range 2019 SSD laptop.

If an entry has been deleted in the middle of the archive, Zipflinger will not leave a "hole" there.
This is done in order to be compatible with top-down parsers such as jarsigner or the JDK zip classes. To this effect,
Zipflinger fills empty space with virtual entries (a.k.a a Local File Header with no name, up to
64KiB extra and no Central Directory entry). Alignment is also done via "extra field".

Entry name heuristic:
- Deleting a non-existing entry will fail silently.
- Adding an existing entry will not silently overwrite but will throw an exception instead.

## ZipMap
The mapper only plays a part when opening an existing archive. The goal of the mapper is to locate
all entries via the Central Directories and build a map of the LFHs (Local File Header) , CDRs
(Central Directory Record) and compile these information into a list of Entry. This data is fed to
the FreeStore to build a map of what is currently used in the file and where their is available
space. It is also an efficient way to list entries in a zip archive if it is the only operation
you need to perform.

Note that if a zip contains several entries with the same name, the last entry in CD order
(not top-down) order is kept.

## ZipRepo
If all operations needed are to list entries and read entries content, ZipRepo is the object to use.
It is lightweight compared to a ZipArchive and allows to read entries via an InputStream to exceed
the 2GiB limitation and reduce heap stress.

## Freestore
The freestore behaves like a memory allocator except that is deals with file address space instead
of memory address space. The list of file locations is kept in a double linked list. Two consecutive
free areas are never contiguous. If space is freed, adjacent free blocks are merged together. As a
result, used space is implicitly described by the "gap" between two free blocks.

All write/delete operations in an archive must first go through the freestore.
- When a zip entry is deleted, the entry Location is returned to the FreeStore.
- When a zip entry is added, a Location must be requested to the Freestore.

Allocations alignment is supported. This is to accommodate Android Package Manager optimizations
where a zip entry is directly mmaped. Upon requesting an aligned allocation, an offset must also
be provided because what needs to be aligned is not the ZIP entry but the zip entry payload.

## ZipWriter
All zip write operations are tracked by the Writer. This is done so an accurate map of written
Locations can be generated when the file is closed and enable incremental V2 signing.

## Sources
To add an entry to a zip, Zipflinger is fed sources. Typically two sources ares supported:

- Source (usually BytesSource)
- ZipSource (made of several ZipSourceEntry)

Source are well-suited for payload already located in memory or in a File. The typical usecase
is when an APK needs to be updated with a new file and also V1 signed. The new file will have been
loaded from storage to generate a hash values.

Note that a BytesSource can be built from an InputStream, in which case the the stream is drained
entirely in the BytesSource constructor.

ZipSource allows to transfer entries from one zip to an other. Zero-copy is used to speed up transfer
. Compression type/format is not changed during the transfer. Upon selecting an entry for transfer,
ZipSourceEntry is returned. The handle is only used if alignment needs to be requested.

All sources can be requested to be aligned via the Source.align() method. All sources except for the
ZipSourceEntry can be requested to be uncompressed/re-compressed.

## File properties and symbolic links

Zipflinger will preserve UNIX permissions as found in the Central Directory "external
attribute" entries when transferring entries between zip archives.

By default, zipflinger creates zip entries with "read" and "write" permissions for user, group, and
others. Symbolic links are also followed. If you want to preserve the executable permission or if
you want to not follow symbolic links, you must use the FullFileSource object.

Keep in mind that FullFileSource is a little bit slower to process files since it needs to perform
extra I/O in order to retrieve each properties.

## Memory (heap) stress

If you find that ByteSource stresses the heap too much or if you run out of memory on large entries,
use a LargeFileSource. These use storage to temporarily store the payload and never load it all
in memory. Because this is also done in the Constructor, compression can still be parallelized and
there is little speed impact.

## Performance considerations when using ZipSource

Zipflinger excels at moving zip entries between zip archives thanks to zero-copy transfer. However
using zero-copy is not always possible.

Best case: If no compression change is requested or if both the source and the destination are inflated,
then zero copy transfer will be used and max speed is achieved.

Ok case: If the src is inflated and the dst is deflated, zipflinger cannot zero-copy since the payload
 must be deflated.

Worse case: If both the src and the dst are deflated, there is no way for Zipflinger to know what level
of compression was used to generate the src (this is not part of Deflate specs or Zip container format).
In order to guarantee the deflate level, Zipflinger has not choice but to inflate the
payload and then deflate it at the requested level, even if the compression level are identical.

## Zip64 Support
Zipflinger has full support for zip64 archives. It is able to handle zip64EOCD (more than 65536
entries) with zip64Locator and zip64 extra fields containing 64-bit compressed, uncompressed, and
offset values (archives larger than 4GiB). There is no facility to handle files larger than 2GiB.

## Profiling
To peek inside Zipflinger and understand where walltime is spent, you can run the "profiler" target.
```
tools/base/bazel/bazel run //tools/base/zipflinger:profiler

Profiling with an APK :
  - Total size (MiB)  :   118
  - Num res           :  5000
  - Size res (KiB)    :    16
  - Num dex           :    10
  - Size dex (MiB)    :     4
```
Once the target has run, retrieve the report from the workstation tmp folder. e.g On Linux:
```
cp /tmp/report.json ~/
```
You can examine the report in Chrome via about://tracing. 

Edit time (ms) on a 3Ghz machine with a PM981 NVMe drive.

```
APK Size     NumRes      SizeRes       NumDex       SizeDex       Time (ms)          
 120 MiB      5000       16 KiB         10            4 MiB          27
  60 MiB      2500       16 KiB         10            4 MiB          18
  49 MiB      2500        4 KiB         10            4 MiB          18
```
  
The edit time is dominated by the parsing time (itself dominated by the number of entries).  
