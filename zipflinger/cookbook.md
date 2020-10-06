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
 zip.add(new BytesSource(new File("classes18.dex"), "classes18.dex", Deflater.BEST_SPEED));
 zip.add(new BytesSource(new File("img.png"), "image.png", Deflater.NO_COMPRESSION));
 zip.close();
```

## How to merge two zips into one
```
 ZipArchive zip = new ZipArchive("app.apk");

 ZipSource zipSource1 = ZipSource.selectAll(new File("/path/to/zip1.zip"));
 zip.add(zipSource1);

 ZipSource zipSource2 = ZipSource.selectAll(new File("/path/to/zip2.zip"));
 zip.add(zipSource2);

 zip.close();
```

## How to copy a zip entry from an other zip into an existing apk
```
 ZipArchive zip = new ZipArchive("app.apk");

 ZipSource zipSource = new ZipSource(new File("/path/to/zip1.zip"));

 zipSource.select("classes18.dex", "classes18NewName.dex"); // non-aligned (default)

 ZipSourceEntry alignedEntry = zipSource.select("lib.so", "lib.so"); // aligned
 alignedEntry.align(4);

 zip.addZipSource(zipSource);

 zip.close();
```

## How to iterate over a zip source entries and select only a few
```
 ZipArchive zip = new ZipArchive("app.apk");
 ZipSource zipSource = new ZipSource(new File("/path/to/zip1.zip"));
 for(String name : zipSource.entries().keys()) {
     if (youwantIt) {
         zipSource.select(name, "newName");
     }
 }
 zip.add(zipSource);
 zip.close();
```

## Generate multiple zips from one zip source
Creating a ZipSource is not an I/O free operation since the CD of the source archive has to be parsed.
In the case where one source zip is to be used to generate multiple destination zips, parsing
can be done only once by providing the same ZipMap to each ZipSource.

```
 // The source zip is parsed only once.
 ZipMap map = ZipMap.from(new File("source.zip"));

 ZipSource zipSource1 = new ZipSource(map);
 zipSource1.select("a", "a");
 try(ZipArchive archive = new ZipArchive("dest1.zip")) {
   archive.add(zipSource1);
 }


 ZipSource zipSource2 = new ZipSource(map);
 zipSource2.select("b", "b");
 try(ZipArchive archive = new ZipArchive("dest2.zip")) {
   archive.add(zipSource2);
 }

```

# Add files to a zip and preserve executable permission
```
try(ZipArchive zip = new ZipArchive("archive.zip")) {
  String p = "/path/x";
  int c = Deflater.NO_COMPRESSION;
  zip.add(new FullFileSource(p, "x", c));
}
```

# Add symbolic links to a zip
```
try(ZipArchive zip = new ZipArchive("archive.zip")) {
  String p = "/path/x";
  int c = Deflater.NO_COMPRESSION;
  FullFileSource.Symlink perm = FullFileSource.Symlink.DO_NOT_FOLLOW;
  zip.add(new FullFileSource(p  , "x", c, perm));
}
```

# How to extract content from an archive
```
 try(ZipRepo repo = new ZipRepo("source.zip")) {
   try(InputStream inputStream = repo.getContent("entryName")) {
   ...
   }
 }
```
