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
#ifndef UTILS_FS_DISK_H
#define UTILS_FS_DISK_H

#include <cstdio>
#include <functional>
#include <map>
#include <string>

namespace profiler {

class FileStat {
 public:
  FileStat(const std::string &rel_path, int32_t modify_age_s)
      : rel_path_(rel_path), modify_age_s_(modify_age_s) {}

  // Returns the path of this file, relative to the directory being walked.
  // e.g. if walking /root/dir/ and coming across /root/dir/subdir/file.txt,
  // rel_path will be "subdir/file.txt"
  const std::string &rel_path() const { return rel_path_; }

  // Returns the time, in seconds, since this file was last modified
  int32_t modify_age_s() const { return modify_age_s_; }

 private:
  std::string rel_path_;
  int32_t modify_age_s_;
};

// An interface to various disk utility methods. Used by FileSystem to carry out
// platform dependent file operations.
//
// For child classes implmenting these methods, they should not do too much
// sanity checking (such as, does the file already exists? etc.). The caller
// will be responsible for doing these checks (so that sanity checks will be
// applied consisntently across all implementations)
class Disk {
 public:
  virtual ~Disk() = default;

  virtual bool HasDir(const std::string &dpath) const = 0;

  virtual bool HasFile(const std::string &fpath) const = 0;

  // Create a new directory. A directory should not already exist at this
  // location when this method is called.
  //
  // This method will fail if the necessary parent directories to create this
  // directory don't already exist; the caller should ensure they do.
  virtual bool NewDir(const std::string &dpath) = 0;

  // Create a new file. A file should not already exist at this location when
  // this method is called.
  //
  // This method will fail if the necessary parent directories to create this
  // directory don't already exist; the caller should ensure they do.
  virtual bool NewFile(const std::string &fpath) = 0;

  // Return the time passed, in seconds, since the target file was modified.
  virtual int32_t ModifyAge(const std::string &fpath) const = 0;

  // Update the target file's modified timestamp. Caller should ensure the file
  // exists. This method does NOT create a file if it doesn't already exist.
  virtual void Touch(const std::string &fpath) = 0;

  // Given a path to a directory, walk all files in it, triggering the callback
  // for each file.
  // TODO: This should be done using an iterator, not a lambda
  virtual void WalkFiles(const std::string &path,
                         std::function<void(const FileStat &)> callback) = 0;

  // Read a file's contents all in one pass. This will return the empty string
  // if the file at the target path is in write mode.
  virtual std::string GetFileContents(const std::string &fpath) const = 0;

  // Move the file from the first path to the second path. The caller should
  // ensure the first file is not in write mode and that the second file either
  // doesn't exist or is also not in write mode. The caller should also not
  // call this method with the same path for both arguments.
  virtual bool MoveFile(const std::string &fpath_from,
                        const std::string &fpath_to) = 0;

  // Returns true if the file is in write mode. See also |OpenWriteMode| and
  // |Close|
  virtual bool IsOpenForWrite(const std::string &fpath) const = 0;

  // Put a file into write mode. The file stays in write mode until |Close| is
  // called.
  virtual void OpenForWrite(const std::string &fpath) = 0;

  // Append text to the end of the file at the specified path. This should not
  // be called if the file is not already in write mode.
  virtual bool Append(const std::string &fpath, const std::string &str) = 0;

  // Indication that user is done writing to a file after calling
  // |OpenWriteMode|.
  virtual void Close(const std::string &fpath) = 0;

  // Remove a directory and all of its contents recursively.
  virtual bool RmDir(const std::string &dpath) = 0;

  // Remove a file.
  virtual bool RmFile(const std::string &fpath) = 0;
};

// A default Disk implementation that uses C functions for managing files. This
// has the advantage of being Android friendly, as Android NDK guides all
// recommend using these methods.
class CDisk final : public Disk {
 public:
  ~CDisk() override;
  bool HasDir(const std::string &path) const override;
  bool HasFile(const std::string &path) const override;
  bool NewDir(const std::string &path) override;
  bool NewFile(const std::string &path) override;
  int32_t ModifyAge(const std::string &path) const override;
  void Touch(const std::string &path) override;
  void WalkFiles(const std::string &path,
                 std::function<void(const FileStat &)> callback) override;
  std::string GetFileContents(const std::string &path) const override;
  bool MoveFile(const std::string &path_from,
                const std::string &path_to) override;
  bool IsOpenForWrite(const std::string &path) const override;
  void OpenForWrite(const std::string &path) override;
  bool Append(const std::string &path, const std::string &str) override;
  void Close(const std::string &path) override;
  bool RmDir(const std::string &path) override;
  bool RmFile(const std::string &path) override;

 private:
  std::map<std::string, FILE *> open_files_;
};

}  // namespace profiler

#endif  // UTILS_FS_DISK_H
