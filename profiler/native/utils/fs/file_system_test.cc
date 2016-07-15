/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include "utils/fs/file_system.h"

#include <set>
#include <unordered_map>

#include <gtest/gtest.h>

using profiler::Dir;
using profiler::Disk;
using profiler::File;
using profiler::FileSystem;
using profiler::Path;
using profiler::PathStat;
using std::function;
using std::make_shared;
using std::unordered_map;
using std::set;
using std::string;
using std::vector;

// A VERY simple in-memory disk system, useful for confirming that FileSystem
// operations worked.
// TODO: Convert this into a MemoryDisk, and improve the implementation.
class FakeDisk final : public Disk {
 public:
  bool HasDir(const string &dpath) const override {
    return dirs_.find(dpath) != dirs_.end();
  }

  bool HasFile(const string &fpath) const override {
    return files_.find(fpath) != files_.end();
  }

  bool NewDir(const string &dpath) override {
    dirs_.insert(dpath);
    Touch(dpath);
    return true;
  }

  bool NewFile(const string &fpath) override {
    files_[fpath] = FileData();
    Touch(fpath);
    return true;
  }

  int32_t GetModificationAge(const string &path) const override {
    auto it = timestamps_.find(path);
    if (it != timestamps_.end()) {
      return current_time_s_ - it->second;
    } else {
      return 0;
    }
  }

  void Touch(const string &path) override {
    timestamps_[path] = current_time_s_;
  }

  void WalkDir(const string &dpath,
               function<void(const PathStat &)> callback) const override {
    vector<string> paths;
    for (const auto &file_entry : files_) {
      if (file_entry.first.compare(0, dpath.length(), dpath) == 0) {
        paths.push_back(file_entry.first);
      }
    }
    for (const auto &dir : dirs_) {
      if (dir != dpath && dir.compare(0, dpath.length(), dpath) == 0) {
        paths.push_back(dir);
      }
    }

    for (const auto &full_path : paths) {
      int32_t modification_age_s = GetModificationAge(full_path);
      PathStat::Type type =
          HasDir(full_path) ? PathStat::Type::DIR : PathStat::Type::FILE;

      callback(PathStat(type, dpath, full_path, modification_age_s));
    }
  }

  string GetFileContents(const string &fpath) const override {
    return files_[fpath].contents;
  }

  bool MoveFile(const string &fpath_from, const string &fpath_to) override {
    NewFile(fpath_to);
    OpenForWrite(fpath_to);
    Append(fpath_to, GetFileContents(fpath_from));
    Close(fpath_to);
    RmFile(fpath_from);
    return true;
  }

  bool IsOpenForWrite(const string &fpath) const override {
    return files_[fpath].in_write_mode;
  }

  void OpenForWrite(const string &fpath) override {
    files_[fpath].in_write_mode = true;
  }

  bool Append(const string &fpath, const string &str) override {
    files_[fpath].contents += str;
    return true;
  }

  void Close(const string &fpath) override {
    files_[fpath].in_write_mode = false;
  }

  bool RmDir(const string &dpath) override {
    timestamps_.erase(dpath);
    if (dirs_.erase(dpath) == 1) {
      // Deleting a directory should also delete all contents
      {
        auto it = dirs_.begin();
        while (it != dirs_.end()) {
          if ((*it).compare(0, dpath.length(), dpath) == 0) {
            it = dirs_.erase(it);
          } else {
            it++;
          }
        }
      }

      {
        auto it = files_.begin();
        while (it != files_.end()) {
          if (it->first.compare(0, dpath.length(), dpath) == 0) {
            it = files_.erase(it);
          } else {
            it++;
          }
        }
      }

      return true;
    } else {
      return false;
    }
  }

  bool RmFile(const string &fpath) override {
    timestamps_.erase(fpath);
    return files_.erase(fpath) == 1;
  }

  void SetCurrentTime(int32_t time) { current_time_s_ = time; }

 private:
  class FileData final {
   public:
    string contents;
    bool in_write_mode;
  };

  mutable set<string> dirs_;
  mutable unordered_map<string, FileData> files_;
  mutable unordered_map<string, int32_t> timestamps_;
  int32_t current_time_s_;
};

TEST(Path, PathStandardizationChecks) {
  EXPECT_EQ(Path::Standardize("/a/b/c/"), "/a/b/c");
  EXPECT_EQ(Path::Standardize("/a/////b//c"), "/a/b/c");
  EXPECT_EQ(Path::Standardize("a/b/c"), "/a/b/c");
  EXPECT_EQ(Path::Standardize("a"), "/a");
  EXPECT_EQ(Path::Standardize("/"), "/");
  EXPECT_EQ(Path::Standardize(""), "/");
}

TEST(Path, StripLastChecks) {
  EXPECT_EQ(Path::StripLast("/a/b/c"), "/a/b");
  EXPECT_EQ(Path::StripLast("/a/b"), "/a");
  EXPECT_EQ(Path::StripLast("/a/b.txt"), "/a");
  EXPECT_EQ(Path::StripLast("/a"), "/");
  EXPECT_EQ(Path::StripLast("/"), "/");
}

TEST(FileSystem, RootIsCreatedByDefault) {
  auto disk = make_shared<FakeDisk>();
  FileSystem fs(disk, "/mock/root");
  EXPECT_TRUE(fs.root()->Exists());
  EXPECT_EQ(fs.root()->path(), "/mock/root");
  EXPECT_EQ(fs.root()->name(), "root");
  EXPECT_TRUE(disk->HasDir("/mock/root"));
  EXPECT_TRUE(disk->HasDir("/mock"));
}

TEST(FileSystem, WorksWithPreexistingFiles) {
  auto disk = make_shared<FakeDisk>();
  disk->NewDir("/mock");
  disk->NewDir("/mock/root");
  disk->NewDir("/mock/root/subdir");
  disk->NewFile("/mock/root/subdir/test.txt");

  FileSystem fs(disk, "/mock/root");
  auto subdir = fs.root()->GetDir("subdir");
  EXPECT_TRUE(subdir->Exists());
  EXPECT_TRUE(subdir->GetFile("test.txt")->Exists());
}

TEST(FileSystem, RootCanBeDeleted) {
  auto disk = make_shared<FakeDisk>();
  FileSystem fs(disk, "/mock/root");
  EXPECT_TRUE(fs.root()->Exists());
  fs.root()->Delete();
  EXPECT_FALSE(fs.root()->Exists());
  fs.root()->Create();
  EXPECT_TRUE(fs.root()->Exists());
}

TEST(FileSystem, CannotGoAboveRoot) {
  auto disk = make_shared<FakeDisk>();
  FileSystem fs(disk, "/mock/root");
  EXPECT_EQ(fs.root()->Up(), fs.root());
}

TEST(FileSystem, CanCreateSubdirFromParentDir) {
  auto disk = make_shared<FakeDisk>();
  FileSystem fs(disk, "/mock/root");
  auto subdir = fs.root()->NewDir("subdir");
  EXPECT_TRUE(subdir->Exists());
  EXPECT_EQ(subdir->path(), "/mock/root/subdir");
}

TEST(FileSystem, CanCreateSubdirInPlace) {
  auto disk = make_shared<FakeDisk>();
  FileSystem fs(disk, "/mock/root");
  auto subdir = fs.root()->GetDir("subdir");
  EXPECT_FALSE(subdir->Exists());
  subdir->Create();
  EXPECT_TRUE(subdir->Exists());
}

TEST(FileSystem, UpReturnsExpectedParent) {
  auto disk = make_shared<FakeDisk>();
  FileSystem fs(disk, "/mock/root");
  auto dir = fs.root()->GetDir("a/b/c");
  EXPECT_EQ(dir->path(), "/mock/root/a/b/c");
  EXPECT_EQ(dir->Up()->path(), "/mock/root/a/b");
  EXPECT_EQ(dir->Up()->Up()->path(), "/mock/root/a");
  EXPECT_EQ(dir->Up()->Up()->Up()->path(), "/mock/root");
  // Can't go past root
  EXPECT_EQ(dir->Up()->Up()->Up()->Up()->path(), "/mock/root");
}

TEST(FileSystem, CanCreateFileFromParentDir) {
  auto disk = make_shared<FakeDisk>();
  FileSystem fs(disk, "/mock/root");
  auto file = fs.root()->NewFile("file.txt");
  EXPECT_TRUE(file->Exists());
  EXPECT_EQ(file->name(), "file.txt");
}

TEST(FileSystem, CanCreateFileInPlace) {
  auto disk = make_shared<FakeDisk>();
  FileSystem fs(disk, "/mock/root");
  auto file = fs.root()->GetFile("file.txt");
  EXPECT_FALSE(file->Exists());
  file->Create();
  EXPECT_TRUE(file->Exists());
}

TEST(FileSystem, AllParentDirectoriesAreCreatedForNewDir) {
  auto disk = make_shared<FakeDisk>();
  FileSystem fs(disk, "/mock/root");
  auto subdirs = fs.root()->NewDir("sub1/sub2/sub3");

  EXPECT_TRUE(fs.root()->GetDir("sub1")->Exists());
  EXPECT_TRUE(fs.root()->GetDir("sub1/sub2")->Exists());
  EXPECT_TRUE(fs.root()->GetDir("sub1/sub2/sub3")->Exists());
}

TEST(FileSystem, AllParentDirectoriesAreCreatedForNewFile) {
  auto disk = make_shared<FakeDisk>();
  FileSystem fs(disk, "/mock/root");
  fs.root()->NewFile("sub1/sub2/file.txt");

  EXPECT_TRUE(fs.root()->GetDir("sub1")->Exists());
  EXPECT_TRUE(fs.root()->GetDir("sub1/sub2")->Exists());
  EXPECT_TRUE(fs.root()->GetFile("sub1/sub2/file.txt")->Exists());
}

TEST(FileSystem, CallingNewDirOverExistingDirDeletesIt) {
  auto disk = make_shared<FakeDisk>();
  FileSystem fs(disk, "/mock/root");

  auto c = fs.root()->NewDir("a/b/c");
  EXPECT_TRUE(c->Exists());

  auto a = fs.root()->NewDir("a");

  EXPECT_TRUE(a->Exists());
  EXPECT_FALSE(c->Exists());
}

TEST(FileSystem, CreatingDirInPlaceOverExistingFails) {
  auto disk = make_shared<FakeDisk>();
  FileSystem fs(disk, "/mock/root");

  auto c = fs.root()->NewDir("a/b/c");
  auto a = fs.root()->GetDir("a");
  EXPECT_TRUE(a->Exists());
  EXPECT_TRUE(c->Exists());

  EXPECT_FALSE(a->Create());
}

TEST(FileSystem, CantCreateFileIfDirAlreadyExists) {
  auto disk = make_shared<FakeDisk>();
  FileSystem fs(disk, "/mock/root");

  auto dir = fs.root()->NewDir("a/b/c");
  auto file = fs.root()->NewFile("a/b/c");

  EXPECT_TRUE(dir->Exists());
  EXPECT_FALSE(file->Exists());
}

TEST(FileSystem, CantCreateDirIfFileAlreadyExists) {
  auto disk = make_shared<FakeDisk>();
  FileSystem fs(disk, "/mock/root");

  auto file = fs.root()->NewFile("a/b/c");
  auto dir = fs.root()->NewDir("a/b/c");

  EXPECT_TRUE(file->Exists());
  EXPECT_FALSE(dir->Exists());
}

TEST(FileSystem, DotDotDirectoriesAreNotAllowed) {
  auto disk = make_shared<FakeDisk>();
  FileSystem fs(disk, "/mock/root");
  auto subdir = fs.root()->NewDir("../invalid");
  EXPECT_FALSE(subdir->Exists());
}

TEST(FileSystem, DeletingDirectoryDeletesChildren) {
  auto disk = make_shared<FakeDisk>();
  FileSystem fs(disk, "/mock/root");
  auto d_parent = fs.root()->NewDir("deleteme");
  auto f = d_parent->NewFile("a/b/c/d.txt");
  auto d_child = d_parent->GetDir("a/b");
  EXPECT_TRUE(d_parent->Exists());
  EXPECT_TRUE(d_child->Exists());
  EXPECT_TRUE(f->Exists());
  d_parent->Delete();
  EXPECT_FALSE(d_parent->Exists());
  EXPECT_FALSE(d_child->Exists());
  EXPECT_FALSE(f->Exists());
}

TEST(FileSystem, ConstAccessAllowsReadOnlyView) {
  auto disk = make_shared<FakeDisk>();
  FileSystem fs(disk, "/mock/root");
  fs.root()->NewFile("a/b/c/d.txt");
  fs.root()->NewDir("a/b/c2");

  const FileSystem *cfs = &fs;
  EXPECT_TRUE(cfs->root()->Exists());
  EXPECT_TRUE(cfs->root()->GetDir("a/b/c")->Exists());
  EXPECT_FALSE(cfs->root()->GetDir("1/2/3")->Exists());
  EXPECT_TRUE(cfs->root()->GetFile("a/b/c/d.txt")->Exists());
}

TEST(FileSystem, TouchUpdatesModificationAge) {
  auto disk = make_shared<FakeDisk>();
  disk->SetCurrentTime(100);
  FileSystem fs(disk, "/mock/root");
  auto f = fs.root()->NewFile("file.txt");

  EXPECT_EQ(f->GetModificationAge(), 0);

  disk->SetCurrentTime(200);
  EXPECT_EQ(f->GetModificationAge(), 100);

  f->Touch();
  EXPECT_EQ(f->GetModificationAge(), 0);
}

TEST(FileSystem, NonExistantFilesAlwaysHaveZeroModificationAge) {
  auto disk = make_shared<FakeDisk>();
  disk->SetCurrentTime(100);
  FileSystem fs(disk, "/mock/root");
  auto f = fs.root()->GetFile("file.txt");
  EXPECT_EQ(f->GetModificationAge(), 0);
  disk->SetCurrentTime(200);
  EXPECT_EQ(f->GetModificationAge(), 0);
}

TEST(FileSystem, WalkDirectoriesWorks) {
  auto disk = make_shared<FakeDisk>();
  FileSystem fs(disk, "/mock/root");
  auto d = fs.root()->NewDir("d");
  d->NewFile("f1");
  d->NewFile("f2");
  d->NewFile("a/b/c/f3");

  int path_count = 0;
  d->Walk([&path_count](const PathStat &pstat) { ++path_count; });
  EXPECT_EQ(path_count, 6);
}

TEST(FileSystem, ConstWalkDirectoriesWorks) {
  auto disk = make_shared<FakeDisk>();
  FileSystem fs(disk, "/mock/root");
  auto d = fs.root()->NewDir("d");
  d->NewFile("f1");
  d->NewFile("f2");
  d->NewFile("a/b/c/f3");

  const FileSystem *cfs = &fs;
  auto cd = cfs->root()->GetDir("d");

  int path_count = 0;
  cd->Walk([&path_count](const PathStat &pstat) { ++path_count; });
  EXPECT_EQ(path_count, 6);
}

TEST(FileSystem, WalkDirectoriesReportsCorrectStats) {
  auto disk = make_shared<FakeDisk>();
  disk->SetCurrentTime(100);
  FileSystem fs(disk, "/mock/root");
  auto b = fs.root()->NewDir("a/b");
  fs.root()->NewFile("a/b/c/d/e/f.txt");
  disk->SetCurrentTime(350);

  int file_count = 0;
  b->Walk([&file_count](const PathStat &pstat) {
    if (pstat.type() == PathStat::Type::FILE) {
      ++file_count;
      EXPECT_EQ(pstat.rel_path(), "c/d/e/f.txt");
      EXPECT_EQ(pstat.full_path(), "/mock/root/a/b/c/d/e/f.txt");
      EXPECT_EQ(pstat.modification_age(), 250);
    }
  });

  EXPECT_EQ(file_count, 1);
}

TEST(FileSystem, CanWriteToFile) {
  auto disk = make_shared<FakeDisk>();
  FileSystem fs(disk, "/mock/root");
  auto f = fs.root()->NewFile("test.txt");
  EXPECT_EQ(f->Contents(), "");
  f->OpenForWrite();
  f->Append("Hello");
  f->Append(" World");
  f->Close();
  EXPECT_EQ(f->Contents(), "Hello World");
}

TEST(FileSystem, CannotWriteToFileNotInWriteMode) {
  auto disk = make_shared<FakeDisk>();
  FileSystem fs(disk, "/mock/root");
  auto f = fs.root()->NewFile("test.txt");
  EXPECT_EQ(f->Contents(), "");
  f->Append("Hello");
  EXPECT_EQ(f->Contents(), "");
  f->Append(" World");
}

TEST(FileSystem, CannotReadFromFileInWriteMode) {
  auto disk = make_shared<FakeDisk>();
  FileSystem fs(disk, "/mock/root");
  auto f = fs.root()->NewFile("test.txt");
  f->OpenForWrite();
  f->Append("Hello World");
  EXPECT_EQ(f->Contents(), "");
  f->Close();
  EXPECT_EQ(f->Contents(), "Hello World");
}

TEST(FileSystem, CanAppendUsingShiftOperator) {
  auto disk = make_shared<FakeDisk>();
  FileSystem fs(disk, "/mock/root");
  auto f = fs.root()->NewFile("test.txt");
  f->OpenForWrite();
  *f << "123 * 456 == " << 123 * 456 << '\n';
  f->Close();

  EXPECT_EQ(f->Contents(), "123 * 456 == 56088\n");
}

TEST(FileSystem, DeletingFileRemovesContents) {
  auto disk = make_shared<FakeDisk>();
  FileSystem fs(disk, "/mock/root");
  auto f = fs.root()->NewFile("test.txt");
  f->OpenForWrite();
  f->Append("Goodbye");
  f->Close();
  EXPECT_EQ(f->Contents(), "Goodbye");
  f->Delete();
  EXPECT_EQ(f->Contents(), "");
}

TEST(FileSystem, DeletingDirectoryUnderFileRemovesContents) {
  auto disk = make_shared<FakeDisk>();
  FileSystem fs(disk, "/mock/root");
  auto f = fs.root()->NewFile("a/b/c/d/test.txt");
  f->OpenForWrite();
  f->Append("Goodbye");
  f->Close();
  EXPECT_EQ(f->Contents(), "Goodbye");
  fs.root()->Delete();
  EXPECT_EQ(f->Contents(), "");
}

TEST(FileSystem, WritesToNonExistantFileAreIgnored) {
  auto disk = make_shared<FakeDisk>();
  FileSystem fs(disk, "/mock/root");
  auto f = fs.root()->GetFile("test.txt");
  EXPECT_FALSE(f->Exists());
  f->OpenForWrite();
  f->Append("Hello World");
  f->Close();
  EXPECT_EQ(f->Contents(), "");
}

TEST(FileSystem, DeletingFileClosesIt) {
  auto disk = make_shared<FakeDisk>();
  FileSystem fs(disk, "/mock/root");
  auto f = fs.root()->NewFile("test.txt");
  f->OpenForWrite();

  EXPECT_TRUE(disk->IsOpenForWrite("/mock/root/test.txt"));

  f.reset();
  EXPECT_FALSE(disk->IsOpenForWrite("/mock/root/test.txt"));
}

TEST(FileSystem, CreatingFileInPlaceOverExistingFileFails) {
  auto disk = make_shared<FakeDisk>();
  FileSystem fs(disk, "/mock/root");
  auto f = fs.root()->NewFile("file.txt");
  f->OpenForWrite();
  f->Append("Hello World");
  f->Close();
  EXPECT_EQ(f->Contents(), "Hello World");

  EXPECT_FALSE(f->Create());
  EXPECT_EQ(f->Contents(), "Hello World");
}

TEST(FileSystem, CallingNewFileOverExistingFileDeletesIt) {
  auto disk = make_shared<FakeDisk>();
  FileSystem fs(disk, "/mock/root");
  auto f = fs.root()->NewFile("file.txt");
  f->OpenForWrite();
  f->Append("Hello World");
  f->Close();
  EXPECT_EQ(f->Contents(), "Hello World");

  fs.root()->NewFile("file.txt");
  EXPECT_EQ(f->Contents(), "");
}

TEST(FileSystem, MovingFileWorks) {
  auto disk = make_shared<FakeDisk>();
  FileSystem fs(disk, "/mock/root");
  auto f1 = fs.root()->NewFile("f1.txt");
  auto f2 = fs.root()->GetFile("f2.txt");
  f1->OpenForWrite();
  f1->Append("Test contents");
  f1->Close();

  EXPECT_TRUE(f1->Exists());
  EXPECT_EQ(f1->Contents(), "Test contents");
  EXPECT_FALSE(f2->Exists());

  f1->MoveContentsTo(f2);
  EXPECT_FALSE(f1->Exists());
  EXPECT_TRUE(f2->Exists());
  EXPECT_EQ(f2->Contents(), "Test contents");
}

TEST(FileSystem, MovingFileFailsIfSrcFileDoesntExist) {
  auto disk = make_shared<FakeDisk>();
  FileSystem fs(disk, "/mock/root");
  auto f1 = fs.root()->GetFile("f1.txt");
  auto f2 = fs.root()->NewFile("f2.txt");
  f2->OpenForWrite();
  f2->Append("Not overwritten");
  f2->Close();

  EXPECT_FALSE(f1->Exists());
  EXPECT_TRUE(f2->Exists());

  f1->MoveContentsTo(f2);
  EXPECT_FALSE(f1->Exists());
  EXPECT_TRUE(f2->Exists());
  EXPECT_EQ(f2->Contents(), "Not overwritten");
}

TEST(FileSystem, MovingFileFailsIfSrcIsInWriteMode) {
  auto disk = make_shared<FakeDisk>();
  FileSystem fs(disk, "/mock/root");
  auto f1 = fs.root()->NewFile("f1.txt");
  auto f2 = fs.root()->GetFile("f2.txt");
  f1->OpenForWrite();
  f1->Append("Not moved");
  f1->Close();

  f1->OpenForWrite();
  f1->MoveContentsTo(f2);
  f1->Close();
  EXPECT_TRUE(f1->Exists());
  EXPECT_EQ(f1->Contents(), "Not moved");
  EXPECT_FALSE(f2->Exists());
}

TEST(FileSystem, MovingFileFailsIfDestIsInWriteMode) {
  auto disk = make_shared<FakeDisk>();
  FileSystem fs(disk, "/mock/root");
  auto f1 = fs.root()->NewFile("f1.txt");
  auto f2 = fs.root()->NewFile("f2.txt");
  f1->OpenForWrite();
  f1->Append("Not moved");
  f1->Close();

  f2->OpenForWrite();
  f1->MoveContentsTo(f2);
  f2->Close();

  EXPECT_TRUE(f1->Exists());
  EXPECT_EQ(f1->Contents(), "Not moved");
  EXPECT_TRUE(f2->Exists());
  EXPECT_EQ(f2->Contents(), "");
}

TEST(FileSystem, MovingFileIsNoOpIfFileIsMovedInPlace) {
  auto disk = make_shared<FakeDisk>();
  FileSystem fs(disk, "/mock/root");
  auto f1 = fs.root()->NewFile("f1.txt");
  auto f2 = fs.root()->GetFile("f1.txt");
  f1->OpenForWrite();
  f1->Append("Test contents");
  f1->Close();

  EXPECT_TRUE(f1->Exists());
  EXPECT_EQ(f1->Contents(), "Test contents");
  EXPECT_TRUE(f2->Exists());
  EXPECT_EQ(f2->Contents(), "Test contents");

  f1->MoveContentsTo(f2);
  EXPECT_TRUE(f1->Exists());
  EXPECT_EQ(f1->Contents(), "Test contents");
  EXPECT_TRUE(f2->Exists());
  EXPECT_EQ(f2->Contents(), "Test contents");
}
