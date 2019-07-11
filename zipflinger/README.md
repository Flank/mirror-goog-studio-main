# Zipflinger

Zipflinger is a library dedicated to ZIP files manipulation. It can create an archive from scratch
but also add/remove entries without decompressing/compressing the whole archive.

Partial zip64 format is supported (with full-support coming in next CL):
   - Archive with more than 65,536 entries can be read.
   - Archive with entries bigger than 4 GiB are not supported.
   - Archive with entries further than 4 GiB are not supported.

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

In order to avoid creating holes when editing an archive, zipflinger recommends (but does not enforce) submitting all delete operations first and then submit add operations. A "deferred add" mechanism was initially used where
delete operations were carried immediately but additions were deferred until the archive was closed.
This approach was ultimately abandoned since it increased the memory footprint significantly when 
BytesSource were involved.

## ZipArchive
ZipArchive is the interface to the users of the library. This is where an archive is created and/or
modified. Typically an user will provide the path to an archive and request operations such as
add/delete.

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
 FileSource source = new FileSource("/path/to/file", "entryName");
 archive.addFile(source);

 // Don't forget to close in order to release the archive fd/handle.
 archive.close();
```

Such an operation can be performed by Zipflinger in under 100 ms with a mid-range 2019 SSD laptop.

If an entry has been deleted
in the middle of the archive, Zipflinger will not leaves a "hole" there. This is done in order to be
compatible with top-down parsers such as jarsigner or the JDK zip classes. To this effect, 
Zipflinger fills empty space with virtual entries (a.k.a a Local File Header with no name, up to 
64KiB extra and no Central Directory entry). Alignment is also done via "extra field".

Entry name heuristic:
- Deleting a non-existing entry will fail silently.
- Adding an existing entry will not silently overwrite but will throw an exception instead.

## Zipmapper
The mapper only plays a part when opening an existing archive. The goal of the mapper is to locate
all entries via the Central Directories and build a map of the LRs (Local Record) , CDRs
(Central Directory Record) and compile these information into a list of Entry. This data is fed to
the FreeStore to build a map of what is currently used in the file and where their is available
space.

Note that if a zip contains several entries with the same name, the last entry in CD order
(not top-down) order is kept.

## Freestore
The freestore behaves like a memory allocator except that is deals with file address space instead
of memory address space. The list of file locations is kept in a double linked list. Two consecutive
free areas are never contiguous. If space is freed, adjacent free blocks are merged together. As a
result, used space is implicitly described by the "gap" between two free blocks.

All write/delete operations in an archive must first go through the freestore.
- When a zip entry is deleted, both the LR and CDR Location are returned to the FreeStore. 
- When a zip entry is added, a Location must be requested to the Freestore.

Allocations alignment is supported on a 4 bytes basis . This is to accommodate Android Package 
Manager optimizations where a zip entry is directly mmaped. Upon requesting an aligned allocation,
an offset must also be provided because whas needs to be aligned is not the ZIP entry but the
zip entry payload.

## ZipWriter
All zip write operations are tracked by the Writer. This is done so an accurate map of written
Locations can be generated when the file is closed and enable incremental V2 signing.

## Sources
To add an entry to a zip, Zipflinger is fed sources. Four types of sources ares supported:

- FileSource
- InputStreamSource
- ZipSource (made of several ZipSourceEntry)
- BytesSource

FileSource are used to connect a ZipArchive to a file. Content is loaded when ZipArchive.close() is
invoked in order to reduce the memory footprint.

InputStreamSource are used to connect a ZipArchive with an InputStream. The stream is drained when
ZipArchive.close() is called.

ZipSource allows to transfer entries from one zip to an other. Zero-copy is used to speed up transfer
. Compression type/format is not changed during the transfer. Upon selecting an entry for transfer,
ZipSourceEntry is returned. The handle is only used if alignment needs to be requested.

BytesSource are well-suited for payload already located in memory. The typical usecase is when an
APK needs to be updated with a new file and also V1 signed. The new file will have been loaded from
storage to generate a hash values. While the file bytes are loaded in memory they can be turned into
a source for a zip entry.

All sources can be requested to be aligned via the Source.align() method. All sources except for the
ZipSourceEntry can be requested to be compressed.