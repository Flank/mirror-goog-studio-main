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

#include "tools/base/deploy/installer/apk_archive.h"

#include <iostream>

#include <fcntl.h>
#include <libgen.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <cstring>

#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/io.h"
#include "tools/base/deploy/common/trace.h"

namespace deploy {

ApkArchive::ApkArchive(const std::string& path) : start_(nullptr), size_(0) {
  ready_ = Prepare(path);
}

ApkArchive::~ApkArchive() {
  munmap(start_, size_);
}

ApkArchive::Location ApkArchive::GetSignatureLocation(
    size_t offset_to_cdrecord) noexcept {
  Location location;
  uint8_t* cdRecord = start_ + offset_to_cdrecord;

  // Check if there is a v2/v3 Signature block here.
  uint8_t* signature = cdRecord - 16;
  if (signature >= start_ &&
      !memcmp((const char*)signature, "APK Sig Block 42", 16)) {
    // This is likely a signature block.
    location.size = *(uint64_t*)(signature - 8);
    location.offset = offset_to_cdrecord - location.size - 8;

    // Check we have the block size at the start and at the end match.
    if (*(uint64_t*)(start_ + location.offset) == location.size) {
      location.valid = true;
    }
  }
  return location;
}

size_t ApkArchive::GetArchiveSize(const std::string& path) const noexcept {
  struct stat st;
  IO::stat(path, &st);
  return st.st_size;
}

uint8_t* ApkArchive::FindEndOfCDRecord() const noexcept {
  constexpr int kMinEndCDRecordSize = 21;
  constexpr int endCDSignature = 0x06054b50;

  // Start scanning from the end
  uint8_t* cursor = start_ + size_ - 1 - kMinEndCDRecordSize;

  // Search for End of Central Directory record signature.
  while (cursor >= start_) {
    if (*(int32_t*)cursor == endCDSignature) {
      return cursor;
    }
    cursor--;
  }
  return nullptr;
}

ApkArchive::Location ApkArchive::FindCDRecord(const uint8_t* cursor) noexcept {
  struct ecdr_t {
    uint8_t signature[4];
    uint16_t diskNumber;
    uint16_t numDisk;
    uint16_t diskEntries;
    uint16_t numEntries;
    uint32_t crSize;
    uint32_t offsetToCdHeader;
    uint16_t commnetSize;
    uint8_t comment[0];
  } __attribute__((packed));
  ecdr_t* header = (ecdr_t*)cursor;

  Location location;
  location.offset = header->offsetToCdHeader;
  location.size = header->crSize;
  location.valid = true;
  return location;
}

ApkArchive::Location ApkArchive::GetCDLocation() noexcept {
  constexpr int cdRecordFileHeaderSignature = 0x02014b50;
  Location location;

  // Find End of Central Directory Record
  uint8_t* cursor = FindEndOfCDRecord();
  if (cursor == nullptr) {
    std::cerr << "Unable to find End of Central Directory record." << std::endl;
    return location;
  }

  // Find Central Directory Record
  location = FindCDRecord(cursor);
  if (cdRecordFileHeaderSignature != *(uint32_t*)(start_ + location.offset)) {
    std::cerr << "Unable to find Central Directory File Header." << std::endl;
    return location;
  }

  location.valid = true;
  return location;
}

bool ApkArchive::Prepare(const std::string& path) noexcept {
  Trace traceDump("Prepare");
  // Search End of Central Directory Record
  int fd = IO::open(path, O_RDONLY, 0);
  if (fd == -1) {
    std::cerr << "Unable to open file '" << path << "'" << std::endl;
    return false;
  }

  size_ = GetArchiveSize(path);

  start_ = (uint8_t*)mmap(0, size_, PROT_READ, MAP_PRIVATE, fd, 0);
  if (start_ == MAP_FAILED) {
    ErrEvent("Unable to mmap file '" + path + "'");
    close(fd);
    return false;
  }

  close(fd);
  return true;
}

std::unique_ptr<std::string> ApkArchive::ReadMetadata(Location loc) const
    noexcept {
  std::unique_ptr<std::string> payload;
  payload.reset(new std::string((const char*)(start_ + loc.offset), loc.size));
  return payload;
}

Dump ApkArchive::ExtractMetadata() noexcept {
  Trace traceDump("ExtractMetadata");

  Dump dump;
  if (!ready_) {
    ErrEvent("Unable to ExtracMetadata (not ready)");
    return dump;
  }

  Location cdLoc = GetCDLocation();
  if (!cdLoc.valid) {
    return dump;
  }
  dump.cd = ReadMetadata(cdLoc);

  Location sigLoc = GetSignatureLocation(cdLoc.offset);
  if (sigLoc.valid) {
    dump.signature = ReadMetadata(sigLoc);
  }
  return dump;
}

}  // namespace deploy
