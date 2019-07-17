#include "tools/base/deploy/installer/tests/fake_device.h"

#include <sys/stat.h>
#include <unistd.h>
#include <thread>

#include <grpc++/grpc++.h>
#include <grpc/grpc.h>
#include "tools/base/deploy/common/env.h"
#include "tools/base/deploy/common/log.h"
#include "tools/base/deploy/installer/tests/fake_device.grpc.pb.h"
#include "tools/base/deploy/installer/tests/fake_device.pb.h"

namespace deploy {

FakeDevice::FakeDevice() {
  std::string addr = std::string("localhost:") + std::to_string(Env::port());
  std::shared_ptr<grpc::Channel> channel =
      grpc::CreateChannel(addr.c_str(), grpc::InsecureChannelCredentials());
  client_ = FakeDeviceService::NewStub(channel);
}

void FakeDevice::RecordCommand(const std::string& command) {
  RecordCommandRequest request;
  RecordCommandResponse response;

  request.set_command(command);

  grpc::ClientContext context;
  client_->RecordCommand(&context, request, &response);
}

void write_to_device(grpc::ClientReaderWriterInterface<ShellCommand, CommandResponse>* writer, int exit_fd) {
  char buffer[8192];

  do {
    fd_set fds;
    FD_ZERO(&fds);
    FD_SET(STDIN_FILENO, &fds);
    FD_SET(exit_fd, &fds);
    if (select(exit_fd + 1, &fds, nullptr, nullptr, nullptr) >= 0) {
      if (FD_ISSET(STDIN_FILENO, &fds)) {
        int r = read(STDIN_FILENO, buffer, 8192);
        if (r <= 0) {
          break;
        }
        ShellCommand command;
        command.set_stdin(buffer, r);
        writer->Write(command);
      }
      if (FD_ISSET(exit_fd, &fds)) {
        // Time to stop reading.
        break;
      }
    }
  } while (true);
}

int FakeDevice::ExecuteCommand(const std::string& cmd) {
  grpc::ClientContext context;
  std::unique_ptr<
      grpc::ClientReaderWriterInterface<ShellCommand, CommandResponse>>
      reader_writer = client_->ExecuteCommand(&context);

  ShellCommand command;
  command.set_command(cmd);
  command.set_uid(Env::uid());
  reader_writer->Write(command);

  int exit_pipe[2];
  pipe(exit_pipe);
  std::thread write_t(write_to_device, reader_writer.get(), exit_pipe[0]);

  CommandResponse response;
  int ret = 0;
  while (!response.terminate() && reader_writer->Read(&response)) {
    std::string stdout = response.stdout();
    int rem = stdout.size();
    const char* buffer = stdout.data();
    while (rem > 0) {
      int w = write(STDOUT_FILENO, buffer, rem);
      if (w <= 0) {
        break;
      }
      buffer += w;
      rem -= w;
    }
    ret = response.exit_code();
  }
  // Write a byte to the exit pipe to unblock the writing thread.
  write(exit_pipe[1], "", 1);
  write_t.join();
  reader_writer->WritesDone();
  reader_writer->Finish();
  return ret;
}

bool FakeDevice::Exists(const std::string& path) {
  struct stat dummy;
  std::string to_exec = Env::root() + path;
  return stat(to_exec.c_str(), &dummy) == 0;
}

int FakeDevice::GetAppUid(const std::string& package) {
  GetAppUidRequest request;
  GetAppUidResponse response;

  request.set_package(package);

  grpc::ClientContext context;
  client_->GetAppUid(&context, request, &response);

  return response.uid();
}

}  // namespace deploy
