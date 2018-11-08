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

#include "tools/base/deploy/installer/delta_push.h"

#include <assert.h>
#include <fcntl.h>
#include <sys/mman.h>

#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/trace.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/proto/deploy.pb.h"

namespace deploy {

DeltapushCommand::DeltapushCommand() {}

namespace {

#ifdef __APPLE__
void fastCopy(const std::string& src, const std::string& dst) {
  int source = open(src.c_str(), O_RDONLY);
  int dest = open(dst.c_str(), O_WRONLY);
  char buffer[8192];

  while (1) {
    ssize_t result = read(source, buffer, sizeof(buffer));
    if (!result) {
      break;
    }
    write(dest, buffer, result);
  }
  close(source);
  close(dest);
}
#else
#include <sys/sendfile.h>
void fastCopy(const std::string& src, const std::string& dst) {
  Phase p("fastCopy");
  int source = open(src.c_str(), O_RDONLY, 0);
  int dest = open(dst.c_str(), O_WRONLY | O_CREAT, 0644);

  // Retrieve how many bytes we need to copy.
  struct stat stat_source;
  fstat(source, &stat_source);
  ssize_t size = stat_source.st_size;

  // Actually copy things here.
  ssize_t bytes_sent = 0;
  off_t offset = 0;
  while (size > 0) {
    bytes_sent = sendfile(dest, source, &offset, size);
    size -= bytes_sent;
  }

  close(source);
  close(dest);
}
#endif
}  // namespace

void DeltapushCommand::ParseParameters(int argc, char** argv) {
  Phase p("Parsing input");
  deploy::MessagePipeWrapper wrapper(STDIN_FILENO);
  std::string data;
  if (!wrapper.Read(&data)) {
    ErrEvent("Unable to read data on stdin.");
    return;
  }

  if (!request_.ParseFromString(data)) {
    ErrEvent("Unable to parse protobuffer request object.");
    return;
  }
  ready_to_run_ = true;
}

void DeltapushCommand::Run(Workspace& workspace) {
  Phase p("Command Deltapush");

  proto::DeltaPushResponse* response = new proto::DeltaPushResponse();
  workspace.GetResponse().set_allocated_deltapush_response(response);

  std::string dst_base = workspace.GetTmpFolder();
  dst_base += to_string(GetTime()) + "-";

  for (const proto::PatchInstruction& patch : request_.patchinstructions()) {
    // Construct string for the destination apk
    const std::string& src_absolute_path = patch.src_absolute_path();
    size_t slash_index = src_absolute_path.find_last_of('/');
    std::string dst_filename = src_absolute_path.substr(slash_index + 1);
    std::string dst_apk_path = dst_base + dst_filename;
    response->add_apks_absolute_paths(dst_apk_path);

    // Copy the full apk to the destination
    fastCopy(src_absolute_path, dst_apk_path.c_str());

    // Patch the apk now
    const std::string& patches = patch.patches();
    const std::string& instructions = patch.instructions();

    // Create dst file
    int dst_fd = open(dst_apk_path.c_str(), O_WRONLY);

    // Adjust file size to match what must be the final size
    ftruncate(dst_fd, (off_t)patch.dst_filesize());

    BeginPhase("patching");
    // TODO: Optimize this with mmaping the file and writing directly to it.
    //       OR
    //       Optimize this by interleaving src apk and patch writes instead
    //       or using fast copy.
    const uint8_t* dataIterator =
        reinterpret_cast<const uint8_t*>(patches.data());
    const int32_t* instIterator =
        reinterpret_cast<const int32_t*>(instructions.data());

    while (instIterator !=
           (int32_t*)(instructions.data() + instructions.size())) {
      int32_t offset = *instIterator++;
      int32_t length = *instIterator++;
      lseek(dst_fd, offset, SEEK_SET);
      write(dst_fd, dataIterator, length);
      dataIterator += length;
    }
    EndPhase();

    close(dst_fd);
  }

  response->set_status(proto::DeltaPushResponse::OK);
}

}  // namespace deploy
