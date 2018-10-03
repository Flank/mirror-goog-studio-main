/*
 * Copyright (C) 2018 The Android Open Source Project
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

#ifndef INSTALLER_APKARCHIVE_H
#define INSTALLER_APKARCHIVE_H

#include <memory>
#include <string>
#include <vector>

namespace deploy {

struct Dump {
  std::unique_ptr<std::string> cd = nullptr;
  std::unique_ptr<std::string> signature = nullptr;
};

class ApkArchiveTester;

// Manipulates an APK archive. Process it by mmaping it in order to minimize
// I/Os.
class ApkArchive {
 public:
  friend ApkArchiveTester;

  // A convenience struct to store the result of search operation when locating
  // the EoCDr, CDr, and Signature Block.
  struct Location {
    size_t offset = 0;
    size_t size = 0;
    bool valid = false;
  };

  ApkArchive(const std::string& path);
  ~ApkArchive();
  Dump ExtractMetadata() noexcept;

 private:
  std::unique_ptr<std::string> ReadMetadata(Location loc) const noexcept;

  // Retrieve the location of the Central Directory Record.
  Location GetCDLocation() noexcept;

  // Retrieve the location of the signature block starting from Central
  // Directory Record
  Location GetSignatureLocation(size_t offset_to_cdrecord) noexcept;
  size_t GetArchiveSize() const noexcept;

  // Find the End of Central Directory Record, starting from the end of the
  // file.
  uint8_t* FindEndOfCDRecord() const noexcept;

  // Find Central Directory Record, starting from the end of the file.
  Location FindCDRecord(const uint8_t* cursor) noexcept;

  // Open apk and mmap it.
  bool Prepare() noexcept;

  std::string path_;
  uint8_t* start_;
  size_t size_;
  int fd_;
};

}  // namespace deploy

#endif  // INSTALLER_APKARCHIVE_H
