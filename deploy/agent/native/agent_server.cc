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

Status ForwardInstallerToAgents() {
  std::string message;

  // Failure to read from the installer kills the server.
  if (!installer_output.Read(&message)) {
    perror("Failed to read from installer");
    return SERVER_EXIT;
  }

  // Failure to write to an agent prevents the installer from trying to read or
  // write any messages to/from that agent.
  auto agent = std::begin(agent_sockets);
  while (agent != std::end(agent_sockets)) {
    if (!(*agent)->Write(message)) {
      perror("Failed to write to agent");
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
    perror("Failed to read from agent");
    agent_sockets.erase(agent);
    return SERVER_OK;
  }

  // Failure to write to the installer kills the server.
  if (!installer_input.Write(message)) {
    perror("Could not write to installer");
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

int main(int argc, char** argv) {
  // Prevent SIGPIPE from hard-crashing the server.
  signal(SIGPIPE, SIG_IGN);

  if (argc < 3) {
    perror("Expecting number of agents in parameter");
    return EXIT_FAILURE;
  }

  int socket_count = atoi(argv[1]);
  char* socket_name = argv[2];

  // Start a server bound to an abstract socket.
  Socket server;
  if (!server.Open() || !server.BindAndListen(socket_name)) {
    perror("Could not bind to socket");
    return EXIT_FAILURE;
  }

  // Accept socket connections from the agents.
  for (int i = 0; i < socket_count; ++i) {
    Socket* socket = new Socket();
    if (!server.Accept(socket, 1000)) {
      Cleanup();
      return EXIT_FAILURE;
    }
    agent_sockets.emplace(socket);
  }

  MessageLoop();
  Cleanup();
  return EXIT_SUCCESS;
}
