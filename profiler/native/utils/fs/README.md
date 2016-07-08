# file_system.h

`utils/fs/` is a collection of classes for simplifying the management of directories and files. It aims to be a lightweight set of wrapper classes, with the following goals:

* Easy to use
* Platform independent
* Powerful defaults
* Restricted to a directory scope
* Assumes success by default
* Mockable

Together, they make it easy to create, delete, edit, copy, move, overwrite, and walk over files without worrying about all the finicky details and edge cases that many of the C system methods normally require you to worry about.

[TOC]

## How to Use

First, create a `FileSystem` instance set to some absolute path. From this point forward, `FileSystem` prevents you from navigating above this root path.

For example, say you have a folder `/usr/docs` that you want to use as a root folder, and you want to create a file `hello.txt` under the folder `/usr/docs/tutorials/lesson1`. You can accomplish this with the following code:

```c++
#include "utils/fs/file_system.h"
FileSystem fs("/usr/docs");
auto f = fs.root()->NewFile("tutorials/lesson1/hello.txt");
f->OpenForWrite();
f->Append("In this tutorial, ...");
f->Close();
```

We'll cover more `FileSystem` recipes below, but this snippet already highlights some major themes with this API:

* **Scoped directory access** - You cannot take the above `fs` instance and use it to access anywhere outside of the `docs` folder, thereby limiting potential abuse (intentional or accidental). Const accessors are also provided if only read-only access is required.
* **Powerful pefaults** - You don't have to worry about creating parent directories recursively; that is done for you. You don't have to worry about checking if a `hello.txt` file is there first; if so, it will just be overwritten.
* **Assumes success** - The FileSystem API assumes that this type of code works almost all of the time, and therefore that path should be easy to both write and read. Functions return boolean values and the system provides various state query methods if you need them, however.
* **Easy to use** - Smart pointers are leveraged so you never have to worry about managing memory allocation or deletion. _However, you should be careful about holding onto file or directory handles if you don't need them anymore._

## Classes

### FileSystem, File, and Dir

In practice, you only need to know about three classes: `FileSystem`, `File`, and `Dir`.

`FileSystem` is the main entry point for creating an initial root `Dir` (available through `FileSystem::root()`). `File` and `Dir` are handles to, well, files and directories. Note the empasis on _handles_ here. Just because you have a `File` instance doesn't mean an actual file exists.

You don't create `File`s or `Dir`s directly; instead, use `Dir::GetDir`, `Dir::NewDir`, `Dir::GetOrNewDir`, `Dir::GetFile`, `Dir::NewFile`, and `Dir::GetAndNewFile` methods.

```c++
auto d = fs.root()->GetOrNewDir("a/b/c/d");
auto f = d->NewFile("e/f.txt");
```

### Disk

A `Disk` is an interface which specifies a complete list of file / directory utility methods that `FileSystem` depends on. Really, `FileSystem` is just a thin, user-friendly layer lying on top of it, and you may never interact with a `Disk` instance directly.

A default implementation, `CDisk`, is provided based on the various standard C methods for creating files and directories (such as `fopen` and `fwrite`). In theory, you can skip using `FileSystem` and just use a `CDisk` instance to add / remove / modify files directly, as long as you're careful to note the documented assumptions these lower level methods are making.

### Path

There is also the `Path` class, which is just the common base-class for `File` and `Dir`. You should rarely, if ever, need to reference the `Path` class directly, but it is mentioned here for completion.

## Recipes

The following section shows how you might accomplish various common operations using the FileSystem API. For brevity, all recipes assume you already have a `FileSystem fs` instance initialized to some sensible root directory.

### Read from a File

```c++
std::string contents = fs.root()->GetFile("a/b/file.txt")->Contents();
```

### Navigate a directory hierarchy

```c++
auto child1 = fs.root()->GetDir("parent/child1");
auto child2 = child1->Up()->GetDir("child2");
```

### Overwrite a file if it exists (but create otherwise)

```c++
auto f = fs.root()->NewFile("a/b/file.txt");
f->OpenForWrite();
f->Append("<Your contents here>");
f->Close();
```

### Alternate cout-style

```c++
auto f = fs.root()->NewFile("a/b/file.txt");
f->OpenForWrite();
*f << "<Your contents here>" << std::endl;
f->Close();
```

### Append if file exists (but create otherwise)

```c++
auto f = fs.root()->GetOrNewFile("a/b/file.txt");
f->OpenForWrite();
f->Append("<Your appended details here>");
f->Close();
```

### Create a new directory (completely deletes any existing!)

```c++
// For this example, /root/a/b/c/d already exists.
assert(fs.root()->GetDir("a/b/c/d")->Exists());

fs.root()->NewDir("a/b/c");
assert(!fs.root()->GetDir("a/b/c/d")->Exists());

```

### Create a file under a subdirectory

```c++
auto dir = fs.root()->NewDir("a/b/c");
auto file = dir->NewFile("file.txt");
```

### Copy a file

```c++
auto from = fs.root()->GetFile("from/file.txt");
auto to = fs.root()->NewFile("to/file.txt");
assert(from->Exists() && to->Exists());
to->OpenForWrite();
to->Append(from->Contents());
to->Close();
```

### Move a file

```c++
auto from = fs.root()->GetFile("from/file.txt");
assert(from->Exists());
auto to = fs.root()->GetFile("to/file.txt");
from->MoveContentsTo(to);
assert(!from->Exists() && to->Exists());
```

### Delete files that haven't been modified in an hour

```c++
const int EXPIRATION_SECS = 3600; // 1 hour
auto d = fs.root()->GetDir("trash");
d->Walk([d](const PathStat &pstat) {
  if (pstat->type() == PathStat::Type::FILE &&
      pstat->modification_age() >= EXPIRATION_SECS) {
    d->GetFile(pstat.rel_path())->Delete();
  }
});
```

## Gotchas

### Notes about creating files and directories

If you call `Dir::NewFile` where a file already exists, or if you call `Dir::NewDir` where a directory already exists, the method will delete what's currently there.

However, if you call `Dir::NewFile` where a directory exists, or a `Dir::NewDir` where a file exists, then the call will _not_ overwrite the target path. This is because this case is assumed to be an unintentional error, so further progress is blocked. If you find yourself in this situation for some reason, then explicitly delete the existing item first (with one handle) before creating a new one (with the other).

```c++
// Hopefully you never need to do this but...
fs.root()->GetDir("a/b/c")->Delete();
fs.root()->NewFile("a/b/c");
```

_Note that the `Dir::Create` and `File::Create` methods (for creating files and directories in place) will abort if anything exists at that location, file or directory. This behavior deviates from the `Dir::NewDir` and `Dir::NewFile` APIs intentionally, and you are encouraged to prefer `NewDir` and `NewFile` over in-place creation._

_The idea is that when you have a parent directory, you have a better understanding of all the sibling files; but when you have a file or directory handle, you don't necessarily. This implementation choice also allows the `NewDir` and `NewFile` APIs to safely delegate to the in-place `Create` methods._

### File read/write mode

A file can only be in read mode OR write mode at any given time. By default, it is always in read mode except between calls to `File::OpenForWrite` and `File::Close`.

Calling write methods on a `File` that is not in write mode are no-ops; simiarly, calling `File::Contents` on a file in write mode will return the empty string.

Use `File::IsInWriteMode` if you have to worry about this state.

### Touch won't create files

`File::Touch` updates the timestamp of existing files only (unlike its Unix counterpart, which creates a file if one doesn't already exist). If you want to mimic such Unix behavior, the pattern is:
```c++
fs.root()->GetOrNewFile("touch_me.txt")->Touch();
```

### Walking directory content is done through a callback

The API (as of writing this README anyway) doesn't have a method like `vector<File> Dir::GetFiles`. This is because, in most cases, you will only care about some small subset of all the files (say, only files older than a certain time, or those that end with a certain extension). Therefore, it is not worth creating temporary `File` instances for them.

Lambdas are used as a way to inform you of each file while also avoiding the creation of unnecessary intermediate handle classes. However, if you really want a `File` instance, the lambda callback is provided with a path which you can use to instantiate one easily:

```c++
fs.root()->WalkFiles([&fs](const FileStat &fstat) {
  auto file = fs.root()->GetFile(fstat.rel_path());
  ...
});
```
