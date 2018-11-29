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

#include "tools/base/deploy/installer/delta_preinstall.h"

#include <algorithm>

#include <assert.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <sys/wait.h>

#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/trace.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/installer/command_cmd.h"
#include "tools/base/deploy/installer/executor.h"
#include "tools/base/deploy/proto/deploy.pb.h"

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
void Sendfile(int out_fd, int in_fd, off_t* offset, size_t count) {
  ssize_t bytes_sent;
  while (count > 0) {
    if ((bytes_sent = sendfile(out_fd, in_fd, offset, count)) <= 0) {
      if (errno == EINTR || errno == EAGAIN) {
        continue;
      }
      return;
    }
    count -= bytes_sent;
  }
}

}  // namespace
namespace deploy {

DeltaPreinstallCommand::DeltaPreinstallCommand() {}

void DeltaPreinstallCommand::ParseParameters(int argc, char** argv) {
  deploy::MessagePipeWrapper wrapper(STDIN_FILENO);
  std::string data;

  BeginPhase("Reading stdin");
  if (!wrapper.Read(&data)) {
    ErrEvent("Unable to read data on stdin.");
    EndPhase();
    return;
  }
  EndPhase();

  BeginPhase("Parsing input ");
  if (!request_.ParseFromString(data)) {
    ErrEvent("Unable to parse protobuffer request object.");
    EndPhase();
    return;
  }
  EndPhase();

  ready_to_run_ = true;
}

bool DeltaPreinstallCommand::SendApkToPackageManager(
    const proto::PatchInstruction& patch, const std::string& session_id) {
  Phase p("Write to PM");

  // Open a stream to the package manager to write to.
  std::string output;
  std::string error;
  std::vector<std::string> parameters;
  parameters.push_back("package");
  parameters.push_back("install-write");
  parameters.push_back("-S");
  parameters.push_back(to_string(patch.dst_filesize()));
  parameters.push_back(session_id);
  std::string apk = patch.src_absolute_path();
  parameters.push_back(apk.substr(apk.rfind("/") + 1));

  int pm_stdout, pm_stderr, pm_stdin, pid;
  Executor::ForkAndExec("cmd", parameters, &pm_stdin, &pm_stdout, &pm_stderr,
                        &pid);

  // Feed each apk to the apk manager
  const std::string& src_absolute_path = patch.src_absolute_path();
  int src_fd = open(src_absolute_path.c_str(), O_RDONLY);

  // Patch the apk now
  const std::string& patches = patch.patches();
  const std::string& instructions = patch.instructions();

  // Special case where there is no patch, the apk has not changed, feed it back
  // to pm.
  if (patches.size() == 0) {
    Sendfile(pm_stdin, src_fd, nullptr, patch.dst_filesize());
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

  // Write dirty and clean sections to Package Manager.
  while (writeOffset < patch.dst_filesize()) {
    if (writeOffset < dirtyOffset) {
      // if there is non-dirty data before the patch, take it from the previous
      // apk.
      off_t offset = writeOffset;
      size_t cleanLength = dirtyOffset - writeOffset;
      Sendfile(pm_stdin, src_fd, &offset, cleanLength);
      writeOffset += cleanLength;
    } else {
      // otherwise take it from the patch
      write(pm_stdin, dataIterator, length);
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

  // Clean up
  close(pm_stdin);
  close(pm_stdout);
  close(pm_stderr);
  int status;
  waitpid(pid, &status, 0);
  close(src_fd);

  return WIFEXITED(status) && (WEXITSTATUS(status) == 0);
}

void DeltaPreinstallCommand::Run(Workspace& workspace) {
  Phase p("Command DeltaPreinstall");

  proto::DeltaPreinstallResponse* response =
      new proto::DeltaPreinstallResponse();
  workspace.GetResponse().set_allocated_deltapreinstall_response(response);

  // Create a session
  CmdCommand cmd;
  std::string output;
  std::string session_id;
  if (!cmd.CreateInstallSession(&output)) {
    ErrEvent(output);
    response->set_status(proto::DeltaPreinstallResponse::ERROR);
    return;
  } else {
    session_id = output;
    response->set_session_id(session_id);
  }

  for (const proto::PatchInstruction& patch : request_.patchinstructions()) {
    SendApkToPackageManager(patch, session_id);
  }

  response->set_status(proto::DeltaPreinstallResponse::OK);
}

}  // namespace deploy
