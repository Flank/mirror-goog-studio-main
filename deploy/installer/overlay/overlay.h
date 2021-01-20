/*
 * Copyright (C) 2019 The Android Open Source Project
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

#ifndef OVERLAY_H
#define OVERLAY_H

#include <string>

namespace deploy {
// A collection of files - dex files, libraries, resources, apks - that are used
// to replace pieces of a currently installed Android app.
class Overlay {
 public:
  Overlay(const std::string& overlay_folder, const std::string& id)
      : overlay_folder_(overlay_folder), id_(id), is_open_(false) {}
  ~Overlay() = default;

  // Returns true if this overlay exists at the specified path with the provided
  // id. Returns false if no overlay exists or if the id does not match.
  static bool Exists(const std::string& path, const std::string& id);

  // Opens this overlay. This allows modification to be made to files in the
  // overlay. Fails if the overlay cannot be created or if no id file exists.
  bool Open();

  // Writes a file into the overlay at the specified path within the overlay,
  // creating any directories that do not already exist. Fails if the overlay is
  // not open or the file cannot be written.
  //
  // The path specified should be relative to the overlay directory. If a file
  // already exists at that path, it will be overwritten.
  bool WriteFile(const std::string& file_path,
                 const std::string& content) const;

  // Removes the file at the specified path from the overlay. Fails if the
  // overlay is not open or the file cannot be deleted.
  //
  // The path specified should be relative to the overlay directory.
  bool DeleteFile(const std::string& file_path) const;

  // Removes the directory at the specified absolute path from the overlay.
  // Fails if the overlay is not open or the directory cannot be deleted.
  //
  // The path specified should be relative to the overlay directory.
  bool DeleteDirectory(const std::string& dir_path) const;

  // Closes this overlay, preventing further modification and writing the new
  // id to disk. Fails if the overlay is already closed, or if the new id cannot
  // be written.
  bool Commit();

 private:
  // Path to the folder <root>/.overlay/.
  const std::string overlay_folder_;
  // The id to write on overlay commit.
  const std::string id_;
  // Whether this overlay object may be modified or not.
  bool is_open_;
};
}  // namespace deploy

#endif
