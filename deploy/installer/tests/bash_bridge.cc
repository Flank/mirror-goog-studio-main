#include <netdb.h>
#include <netinet/in.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <unistd.h>
#include <string>

int writeInt(int fd, int value) {
  // Convert int to BigEndian so it can be read by a Java InputStream
  char buffer[4];
  buffer[0] = (value >> 24) & 0xff;
  buffer[1] = (value >> 16) & 0xff;
  buffer[2] = (value >> 8) & 0xff;
  buffer[3] = value & 0xff;
  return write(fd, &buffer, 4);
}

// This program acts as a bridge between its stdin/stdout and a socket:
//   - Input pipe (stdin) is read and written to socket input.
//   - Input socket is read and written to output pipe (stdout).
//   - The error pipe stream is never written to.
int main(int argc, char* argv[]) {
  int sockfd, portno, n;

  // (SIGPIP is raised upon attempting to write to a closed pipe/socket).
  // Ignoring SIGPIPE allows to keep on bridging stdin->socket or socket->stdout
  // even when the other stream has been closed.
  signal(SIGPIPE, SIG_IGN);

  portno = atoi(argv[1]);
  sockfd = socket(AF_INET, SOCK_STREAM, 0);

  struct sockaddr_in serv_addr;
  memset(&serv_addr, 0, sizeof(serv_addr));
  serv_addr.sin_family = AF_INET;
  serv_addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
  serv_addr.sin_port = htons(portno);

  if (connect(sockfd, (struct sockaddr*)&serv_addr, sizeof(serv_addr)) != 0) {
    return 1;
  }

  std::string cmd;
  for (int i = 2; i < argc; i++) {
    if (i > 2) {
      cmd += " ";
    }
    cmd += argv[i];
  }
  int size = cmd.size();
  n = writeInt(sockfd, size);
  n = write(sockfd, cmd.c_str(), cmd.size());

  struct pollfd fds[2];
  fds[0].fd = STDIN_FILENO;
  fds[0].events = POLLIN;
  fds[1].fd = sockfd;
  fds[1].events = POLLIN;

  int fdix;
  char buffer[8192];
  while ((fdix = poll(fds, 2, -1)) > 0) {
    if (fds[0].revents & POLLIN) {
      int r = read(STDIN_FILENO, buffer, 8192);
      if (r <= 0) {
        break;
      }
      write(sockfd, buffer, r);
    }
    if (fds[1].revents & POLLIN) {
      int r = read(sockfd, buffer, 8192);
      if (r <= 0) {
        break;
      }
      write(STDOUT_FILENO, buffer, r);
    }
    if (fds[1].revents & (POLLHUP | POLLERR | POLLNVAL)) {
      break;
    }
  }

  close(sockfd);
  return 0;
}
