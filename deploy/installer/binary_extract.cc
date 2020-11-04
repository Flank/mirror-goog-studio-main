/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include "tools/base/deploy/installer/binary_extract.h"

#include <fcntl.h>
#include <sys/stat.h>

#include "tools/base/bazel/native/matryoshka/doll.h"
#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/io.h"
#include "tools/base/deploy/common/utils.h"

namespace {
const int kRwFileMode =
    S_IRUSR | S_IRGRP | S_IROTH | S_IWUSR | S_IWGRP | S_IWOTH;
const int kRwxFileMode = S_IRUSR | S_IXUSR | S_IWUSR | S_IRGRP | S_IXGRP |
                         S_IWGRP | S_IROTH | S_IXOTH | S_IWOTH;
}  // namespace

namespace deploy {

bool ExtractBinaries(const std::string& target_dir,
                     const std::vector<std::string>& files_to_extract) {
  Phase p("ExtractBinaries");

  std::vector<std::unique_ptr<matryoshka::Doll>> dolls;
  for (const std::string& file : files_to_extract) {
    const std::string tmp_path = target_dir + file;

    // If we've already extracted the file, we don't need to re-extract.
    if (IO::access(tmp_path, F_OK) == 0) {
      continue;
    }

    // Open the matryoshka if we haven't already done so.
    if (dolls.empty() && !matryoshka::Open(dolls)) {
      ErrEvent("Installer binary does not contain any other binaries.");
      return false;
    }

    // Find the binary that corresponds to this file and write it to disk.
    matryoshka::Doll* doll = matryoshka::FindByName(dolls, file);
    if (!doll) {
      continue;
    }

    if (!WriteArrayToDisk(doll->content, doll->content_len,
                          target_dir + file)) {
      ErrEvent("Failed writing to disk");
      return false;
    }
  }

  return true;
}

bool WriteArrayToDisk(const unsigned char* array, uint64_t array_len,
                      const std::string& dst_path) {
  Phase p("WriteArrayToDisk");
  int fd = IO::open(dst_path, O_WRONLY | O_CREAT, kRwFileMode);
  if (fd == -1) {
    ErrEvent("WriteArrayToDisk, open: "_s + strerror(errno));
    return false;
  }
  int written = write(fd, array, array_len);
  if (written == -1) {
    ErrEvent("WriteArrayToDisk, write: "_s + strerror(errno));
    return false;
  }

  int close_result = close(fd);
  if (close_result == -1) {
    ErrEvent("WriteArrayToDisk, close: "_s + strerror(errno));
    return false;
  }

  IO::chmod(dst_path, kRwxFileMode);
  return true;
}

}  // namespace deploy