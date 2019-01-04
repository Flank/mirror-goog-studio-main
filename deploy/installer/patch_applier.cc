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

#include "tools/base/deploy/installer/patch_applier.h"

#include <fcntl.h>
#include <unistd.h>

#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/utils.h"

namespace {
#ifdef __APPLE__
ssize_t sendfile(int out_fd, int in_fd, off_t* offset, size_t count) {
  off_t orig;
  char buf[8192];

  // Seek to the correct position in the file.
  if (offset != nullptr) {
    orig = lseek(in_fd, 0, SEEK_CUR);
    if (orig == -1) {
      return -1;
    }
    if (lseek(in_fd, *offset, SEEK_SET) == -1) {
      return -1;
    }
  }

  // Write all data.
  size_t to_sent = 0;
  while (count > 0) {
    int to_read = std::min(sizeof(buf), count);
    int num_read = read(in_fd, buf, to_read);
    if (num_read == -1) {
      return -1;
    }
    if (num_read == 0) {
      break;
    }

    int num_sent = write(out_fd, buf, num_read);
    if (num_sent == -1) {
      return -1;
    }
    count -= num_sent;
    to_sent += num_sent;
  }

  // Deal with offset update and file cursor reset
  if (offset != nullptr) {
    *offset = lseek(in_fd, 0, SEEK_CUR);
    if (*offset == -1) {
      return -1;
    }
    if (lseek(in_fd, orig, SEEK_SET) == -1) {
      return -1;
    }
  }

  return to_sent;
}
#else
#include <sys/sendfile.h>
#endif

// Sendfile based on sendfile but able to retry on fails.
bool Sendfile(int out_fd, int in_fd, off_t* offset, size_t count) {
  ssize_t bytes_sent;
  while (count > 0) {
    if ((bytes_sent = sendfile(out_fd, in_fd, offset, count)) <= 0) {
      if (errno == EINTR || errno == EAGAIN) {
        continue;
      }
      return false;
    }
    count -= bytes_sent;
  }
  return true;
}

}  // namespace

namespace deploy {

bool PatchApplier::ApplyPatchToFD(const proto::PatchInstruction& patch,
                                  int dst_fd) const noexcept {
  const std::string& src_absolute_path = patch.src_absolute_path();
  int src_fd = open(src_absolute_path.c_str(), O_RDONLY);
  if (src_fd == -1) {
    return false;
  }

  // Patch the apk now
  const std::string& patches = patch.patches();
  const std::string& instructions = patch.instructions();

  // Special case where there is no patch, the apk has not changed, feed it back
  // to pm.
  if (patches.size() == 0) {
    Sendfile(dst_fd, src_fd, nullptr, patch.dst_filesize());
    close(src_fd);
    return true;
  }

  const uint8_t* dataIterator =
      reinterpret_cast<const uint8_t*>(patches.data());
  const int32_t* instIterator =
      reinterpret_cast<const int32_t*>(instructions.data());
  int32_t writeOffset = 0;
  size_t instruction_counter = instructions.size() / 8;

  // Consume one instruction
  int32_t dirtyOffset = *instIterator++;
  int32_t length = *instIterator++;
  instruction_counter--;

  // Write dirty and clean sections to destination file descriptor.
  while (writeOffset < patch.dst_filesize()) {
    if (writeOffset < dirtyOffset) {
      // if there is non-dirty data before the next patch, take it from the
      // source apk.
      off_t offset = writeOffset;
      size_t cleanLength = dirtyOffset - writeOffset;
      // TODO Check return of SendFile. This could fail if the disk is full.
      Sendfile(dst_fd, src_fd, &offset, cleanLength);
      writeOffset += cleanLength;
    } else {
      // otherwise take it from the patch
      write(dst_fd, dataIterator, length);
      dataIterator += length;
      writeOffset += length;

      // Consume an instruction or create a fake one if there are no more.
      if (instruction_counter > 0) {
        dirtyOffset = *instIterator++;
        length = *instIterator++;
        instruction_counter--;
      } else {
        dirtyOffset = patch.dst_filesize();
        length = 0;
      }
    }
  }
  close(src_fd);
  return true;
}

}  // namespace deploy
