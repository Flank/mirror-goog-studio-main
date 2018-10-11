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

#include "apk_archive.h"
#include "trace.h"

#include <fcntl.h>
#include <libgen.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <cstring>
#include <iostream>

namespace deploy {

ApkArchive::ApkArchive(const std::string& path)
    : path_(path), start_(nullptr), size_(0), fd_(-1) {}

ApkArchive::~ApkArchive() {
  close(fd_);
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

size_t ApkArchive::GetArchiveSize() const noexcept {
  struct stat st;
  stat(path_.c_str(), &st);
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

bool ApkArchive::Prepare() noexcept {
  // Search End of Central Directory Record
  fd_ = open(path_.c_str(), O_RDONLY, 0);
  if (fd_ == -1) {
    std::cerr << "Unable to open file '" << path_ << "'" << std::endl;
    return false;
  }

  size_ = GetArchiveSize();

  start_ = (uint8_t*)mmap(0, size_, PROT_READ, MAP_PRIVATE, fd_, 0);
  if (start_ == MAP_FAILED) {
    std::cerr << "Unable to mmap file '" << path_ << "'" << std::endl;
    // TODO: (Valid for entire project), ALL errors must explicitly logged to
    // offer as much diagnostic information as possible.
    return false;
  }

  return true;
}

std::unique_ptr<std::string> ApkArchive::ReadMetadata(Location loc) const noexcept {
  std::unique_ptr<std::string> payload;
  payload.reset(new std::string());
  payload->resize(loc.size);
  int fd = open(path_.c_str(), O_RDONLY, 0);
  lseek(fd, loc.offset, SEEK_SET);
  read(fd, (void*)payload->data(), loc.size);
  close(fd);
  return payload;
}

Dump ApkArchive::ExtractMetadata() noexcept {
  Trace traceDump("ExtractMetadata");
  bool readyForProcessing = Prepare();

  Dump dump;
  if (!readyForProcessing) {
    // TODO Log errors better than this!
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
