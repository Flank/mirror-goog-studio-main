#include <signal.h>
#include <cstdlib>
#include <string>
#include <vector>

#include "tools/base/deploy/common/message_pipe_wrapper.h"
#include "tools/base/deploy/common/socket.h"

// Server program that connects to instant run agents for a particular
// application package. The server should be invoked with run-as, and expects an
// agent config proto via standard input.
//
// The server implements a specific protocol between installer and agent(s):
// 1. The installer sends a message to the server.
// 2. The server forwards that message to all agents.
// 3. The agents send zero or more messages to the server.
// 4. The server forwards those messages to the installer.
// 5. Once all agents have closed their connections, the server exits.
//
// Note that the installer only sends one message to the server.

// Pipe used for writing data to the installer.
deploy::MessagePipeWrapper installer_input(STDOUT_FILENO);

// Pipe used for reading data from the installer.
deploy::MessagePipeWrapper installer_output(STDIN_FILENO);

// Socket connections to the JVMTI agents.
std::vector<deploy::MessagePipeWrapper*> agent_sockets;

// Reads messages from the connected agent sockets and writes those messages to
// the server's standard output. Loops until all connected agent sockets return
// an error or disconnect.
void ForwardAgentsToInstaller() {
  while (!agent_sockets.empty()) {
    auto ready = deploy::MessagePipeWrapper::Poll(agent_sockets, -1);
    for (size_t idx : ready) {
      std::string message;

      // If the read returns an error, stop trying to read from this socket.
      if (!agent_sockets[idx]->Read(&message)) {
        agent_sockets.erase(agent_sockets.begin() + idx);
        continue;
      }

      // Forward the message from the agent to the installer.
      if (!installer_input.Write(message)) {
        perror("Could not write to installer");
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

  // Start a server bound to an abstract socket.
  deploy::Socket server;
  if (!server.Open() ||
      !server.BindAndListen(deploy::Socket::kDefaultAddress)) {
    perror("Could not bind to socket");
    return EXIT_FAILURE;
  }

  // Wait for the installer to send us some input before we start accepting
  // connections.
  std::string message;
  if (!installer_output.Read(&message)) {
    perror("Failed to read from installer");
    return EXIT_FAILURE;
  }

  // Accept socket connections from the agents.
  int socket_count = atoi(argv[1]);
  for (int i = 0; i < socket_count; ++i) {
    deploy::Socket* socket = new deploy::Socket();
    if (!server.Accept(socket, 1000)) {
      Cleanup();
      return EXIT_FAILURE;
    }
    agent_sockets.emplace_back(socket);
  }

  // To minimize the chances of getting into an inconsistent state, only send
  // the message after all agents have successfully connected.
  //
  // TODO: What do we do if a write after the first fails?
  for (auto& agent_socket : agent_sockets) {
    if (!agent_socket->Write(message)) {
      Cleanup();
      return EXIT_FAILURE;
    }
  }

  // Continuously poll for agent responses and write them to the installer.
  ForwardAgentsToInstaller();

  Cleanup();
  return EXIT_SUCCESS;
}