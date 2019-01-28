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
 *
 */

#include <string>
#include <unordered_set>
#include <vector>

#include <signal.h>
#include <cstdlib>

#include "tools/base/deploy/common/log.h"
#include "tools/base/deploy/common/message_pipe_wrapper.h"
#include "tools/base/deploy/common/socket.h"

using namespace deploy;

// Server program that connects to instant run agents for a particular
// application package. The server should be invoked with run-as, and expects an
// agent config proto via standard input.

// Pipe used for writing data to the installer.
MessagePipeWrapper installer_input(STDOUT_FILENO);

// Pipe used for reading data from the installer.
MessagePipeWrapper installer_output(STDIN_FILENO);

// Socket connections to the JVMTI agents.
std::unordered_set<MessagePipeWrapper*> agent_sockets;

enum Status { SERVER_OK, SERVER_EXIT };

void LogInfo(const std::string& message) {
  Log::I("[Server] %s", message.c_str());
}

void LogError(const std::string& message) {
  Log::E("[Server] %s", message.c_str());
}

Status ForwardInstallerToAgents() {
  std::string message;

  // Failure to read from the installer kills the server.
  if (!installer_output.Read(&message)) {
    LogError("Failed to read from installer");
    return SERVER_EXIT;
  }

  // Failure to write to an agent prevents the installer from trying to read or
  // write any messages to/from that agent.
  auto agent = std::begin(agent_sockets);
  while (agent != std::end(agent_sockets)) {
    if (!(*agent)->Write(message)) {
      LogInfo("Agent disconnected (write)");
      agent = agent_sockets.erase(agent);
    } else {
      ++agent;
    }
  }

  return SERVER_OK;
}

Status ForwardAgentToInstaller(MessagePipeWrapper* agent) {
  std::string message;

  // Failure to read from an agent prevents the installer from trying to read or
  // write any messages to/from that agent.
  if (!agent->Read(&message)) {
    LogInfo("Agent disconnected (read)");
    agent_sockets.erase(agent);
    return SERVER_OK;
  }

  // Failure to write to the installer kills the server.
  if (!installer_input.Write(message)) {
    LogError("Failed to write to installer");
    return SERVER_EXIT;
  }

  return SERVER_OK;
}

// Reads messages from the connected agent sockets and writes those messages to
// the server's standard output. Loops until all connected agent sockets return
// an error or disconnect.
void MessageLoop() {
  while (!agent_sockets.empty()) {
    std::vector<MessagePipeWrapper*> poll_list(agent_sockets.begin(),
                                               agent_sockets.end());
    poll_list.emplace_back(&installer_output);

    std::vector<size_t> ready = MessagePipeWrapper::Poll(poll_list, -1);
    for (size_t idx : ready) {
      MessagePipeWrapper* fd = poll_list[idx];

      Status status = SERVER_EXIT;
      if (fd == &installer_output) {
        status = ForwardInstallerToAgents();
      } else {
        status = ForwardAgentToInstaller(fd);
      }

      if (status == SERVER_EXIT) {
        return;
      }
    }
  }
}

void Cleanup() {
  for (auto& agent_socket : agent_sockets) {
    delete agent_socket;
  }
}

// The server expects exactly three arguments on startup:
//  agent_count : the number of socket connections the server will wait for.
//  socket_name : the name of the unix domain socket to which the server binds.
//  sync_fd     : the write end of a pipe opened by the parent process which the
//                server will close when it is ready to receive connections. The
//                parent process MUST block until reading EOF from the pipe.
int main(int argc, char** argv) {
  LogInfo("Agent server online");
  // Prevent SIGPIPE from hard-crashing the server.
  signal(SIGPIPE, SIG_IGN);

  if (argc < 4) {
    LogError("Expected arguments: <agent_count>, <socket_name>, <sync_fd>");
    return EXIT_FAILURE;
  }

  int socket_count = atoi(argv[1]);
  char* socket_name = argv[2];

  // The write end of a pipe opened by the parent process. When the server is
  // ready to receive connections, it MUST close this file descriptor to notify
  // the parent process, which MUST block until reading EOF on this pipe to
  // avoid race conditions.
  int sync_fd = atoi(argv[3]);

  // Start a server bound to an abstract socket.
  Socket server;
  if (!server.Open() || !server.BindAndListen(socket_name)) {
    LogError("Could not bind to socket");
    return EXIT_FAILURE;
  }

  // Let the parent process know that it can safely attach agents.
  close(sync_fd);

  // Accept socket connections from the agents.
  for (int i = 0; i < socket_count; ++i) {
    Socket* socket = new Socket();
    // 15 seconds since there is a chance we need to wait for the
    // host to attach an agent from the debugger.
    if (!server.Accept(socket, 15000)) {
      Cleanup();
      return EXIT_FAILURE;
    }
    agent_sockets.emplace(socket);
  }

  MessageLoop();
  Cleanup();
  LogInfo("Agent server offline");
  return EXIT_SUCCESS;
}
