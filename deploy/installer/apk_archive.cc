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

#if defined(ANDROID)
#include <sys/sendfile.h>
#else
#include <stdlib.h>
#include <unistd.h>
ssize_t sendfile(int out_fd, int in_fd, off_t* offset, size_t count) {
  lseek(in_fd, *offset, SEEK_SET);
  void* storage = malloc(count);
  uint8_t* cursor = (uint8_t*)storage;
  ssize_t bytesRead = -1;
  ssize_t bytesRemaining = count;
  while (bytesRead != 0 && bytesRemaining > 0) {
    bytesRead = read(in_fd, cursor, count);
    bytesRemaining -= bytesRead;
    cursor += bytesRead;
  }
  ssize_t written = write(out_fd, storage, count);
  free(storage);
  return written;
}
#endif

namespace deployer {

#define FILE_MODE (S_IRUSR | S_IRGRP | S_IROTH | S_IWUSR | S_IWGRP | S_IWOTH)

ApkArchive::ApkArchive(const std::string& path)
    : path_(path), start_(nullptr), size_(0), fd_(-1) {}

ApkArchive::~ApkArchive() {
  close(fd_);
  munmap(start_, size_);
}

ApkArchive::Location ApkArchive::GetSignatureLocation(
    uint8_t* cdRecord) noexcept {
  Location location;
  location.valid = false;

  // Check if there is a v2/v3 Signature block here.
  uint8_t* signature = cdRecord - 16;
  if (signature >= start_ && !memcmp((const char*)signature,
                                     "APK Sig Block "
                                     "42",
                                     16)) {
    // This is likely a signature block.
    location.size = *(uint64_t*)(signature - 8);
    location.start = cdRecord - location.size - 8;

    // Check we have the block size at the start and at the end match.
    if (*(uint64_t*)location.start == location.size) {
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
  location.start = start_ + header->offsetToCdHeader;
  location.size = header->crSize;
  return location;
}

ApkArchive::Location ApkArchive::GetCDLocation() noexcept {
  constexpr int cdRecordFileHeaderSignature = 0x02014b50;

  Location location;
  location.valid = false;

  // Find End of Central Directory Record
  uint8_t* cursor = FindEndOfCDRecord();
  if (!cursor) {
    std::cerr << "Unable to find End of Central Directory record." << std::endl;
    return location;
  }

  // Find Central Directory Record
  location = FindCDRecord(cursor);

  if (cdRecordFileHeaderSignature != *(uint32_t*)location.start) {
    std::cerr << "Unable to find Central Directory File Header." << std::endl;
  } else {
    location.valid = true;
  }

  return location;
}

bool ApkArchive::Prepare() noexcept {
  // Search End of Central Directory Record
  fd_ = open(path_.c_str(), O_RDONLY, FILE_MODE);
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

bool ApkArchive::WriteMetadata(const std::string& path, Location loc) const
    noexcept {
  int outFd = open(path.c_str(), O_WRONLY | O_CREAT, FILE_MODE);
  off_t offset = (off_t)(loc.start - start_);
  ssize_t byteSent = 0;
  while (byteSent != -1 && loc.size != 0) {
    byteSent = sendfile(outFd, fd_, &offset, loc.size);
    loc.size -= byteSent;
  }
  if (byteSent == -1) {
    std::cerr << "Error while writing metadata for " << path_ << std::endl;
    std::cerr << strerror(errno) << std::endl;
    return false;
  }
  close(outFd);
  return true;
}

bool ApkArchive::ExtractMetadata(const std::string& packageName,
                                 const std::string& dumpBase) noexcept {
  Trace traceDump("ExtractMetadata");
  bool readyForProcessing = Prepare();

  if (!readyForProcessing) {
    // TODO Log errors better than this!
    return false;
  }

  std::string apkFilename = std::string(strrchr(path_.c_str(), '/') + 1);

  // TODO: Unlink everything in dumpBase to prevent conflict from previous dump

  Location cdLoc = GetCDLocation();
  if (cdLoc.valid) {
    std::string cdDumpPath = dumpBase + apkFilename + ".remotecd";
    WriteMetadata(cdDumpPath, cdLoc);
  } else {
    // Without a valid Central Directory location, we cannot get a valid
    // Signature location either.
    return false;
  }

  Location sigLoc = GetSignatureLocation(cdLoc.start);
  if (sigLoc.valid) {
    std::string blockDumpPath = dumpBase + apkFilename + ".remoteblock";
    WriteMetadata(blockDumpPath, sigLoc);
  }
  return true;
}

}  // namespace deployer
