# Zipflinger cheatsheet

Zipflinger is a library dedicated to ZIP files manipulation.  It is capable of adding and removing
entries without decompressing/compressing the whole archive. It supports:

- Listing content of a zip archive.
- Deleting entry in an archive.
- Adding entries in an archive with source from filesystem, memory, and other zip archives.

Deleting a non-existing entry will fail silently.
Adding an existing entry will not silently overwrite but will throw an exception instead.

## How to list the content of an archive
```
Map<String, Entry> map = ZipArchive.listEntries(new File("/path/to/zip"));
for(Entry entry : map.getEntries().values()) {
    entry.getName();
    entry.getCrc();
    ...
}
```

## How to replace an entry in an archive
```
 ZipArchive zip = new ZipArchive("app.apk");
 zip.delete("classes18.dex"); // All deletes must be submitted first.
 zip.add(new FileSource("/path/to/classes18.dex", "classes18.dex", true)); // Compressed
 zip.add(new FileSource("/path/to/stuff.png", "image.png", false)); // Uncompressed
 zip.close();
```

## How to merge two zips into one
```
 ZipArchive zip = new ZipArchive("app.apk");
 ZipSource zipSource1 = ZipSource.selectAll(new File("/path/to/zip1.zip")):
 ZipSource zipSource2 = ZipSource.selectAll(new File("/path/to/zip2.zip")):
 zip.add(zipSource1);
 zip.add(zipSource2);
 zip.close();
```

## How to copy a zip entry from an other zip into an existing apk
```
 ZipArchive zip = new ZipArchive("app.apk");
 ZipSource zipSource = new ZipSource(new File("/path/to/zip1.zip")):

 zipSource.select("classes18.dex", "classes18NewName.dex"); // non-aligned (default)

 ZipSourceEntry alignedEntry = zipSource.select("lib.so", "lib.so"); // aligned
 alignedEntry.align();

 zip.addZipSource(zipSource);
 zip.close();
```

## How to iterate over a zip source entries and select only a few
```
 ZipArchive zip = new ZipArchive("app.apk");
 ZipSource zipSource = new ZipSource(new File("/path/to/zip1.zip")):
 for(String name : zipSource.entries().keys()) {
     if (youwantIt) {
         zipSource.select(name, "newName");
     }
 }
 zip.add(zipSource);
 zip.close();
```