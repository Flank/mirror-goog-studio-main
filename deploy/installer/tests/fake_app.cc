
#include <dlfcn.h>
#include <execinfo.h>
#include <fcntl.h>
#include <grpc++/grpc++.h>
#include <jni.h>
#include <jvmti.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/uio.h>
#include <condition_variable>
#include <mutex>
#include <string>

#include "tools/base/deploy/common/env.h"
#include "tools/base/deploy/common/log.h"
#include "tools/base/deploy/common/utils.h"
#include "tools/base/deploy/installer/tests/fake_device.grpc.pb.h"
#include "tools/base/deploy/installer/tests/fake_device.pb.h"
#include "tools/base/deploy/installer/tests/fake_vm.h"

using AgentOnLoadFunction = jint (*)(JavaVM*, const char*, void*);

deploy::FakeJavaVm java_vm;
std::atomic<bool> running(true);
std::condition_variable condition;
std::mutex edt_mutex;
std::vector<std::function<void()>> events;

class FakeAppImpl final : public FakeApp::Service {
 public:
  grpc::Status AttachAgent(grpc::ServerContext* context,
                           const AttachAgentRequest* request,
                           AttachAgentResponse* response) override {
    std::string path = request->path();
    std::string options = request->options();
    auto callback = [path, options]() -> void {
      std::string real_path = deploy::Env::root();
      real_path += path;
      void* agent = dlopen(real_path.c_str(), RTLD_LAZY);
      if (agent == nullptr) {
        deploy::Log::E("Cannot attach agent %s", path.c_str());
        return;
      }
      void* sym = dlsym(agent, "Agent_OnAttach");
      AgentOnLoadFunction f = (AgentOnLoadFunction)sym;
      (*f)(&java_vm, options.c_str(), nullptr);
    };

    if (!request->blocking()) {
      std::unique_lock<std::mutex> lock(edt_mutex);
      events.push_back(callback);
    } else {
      callback();
    }

    condition.notify_all();
    return grpc::Status::OK;
  }
};

void BacktraceSignalHandler(int signal) {
  void* array[10];
  size_t size = backtrace(array, 10);
  std::string logcat = deploy::Env::logcat();
  int fd = open(logcat.c_str(), O_RDWR | O_APPEND);
  backtrace_symbols_fd(array, size, fd);
  close(fd);
  exit(1);
}

int main(int argc, char* argv[]) {
  signal(SIGSEGV, BacktraceSignalHandler);

  FakeAppImpl service;

  int port;
  grpc::ServerBuilder builder;
  builder.AddListeningPort("localhost:0", grpc::InsecureServerCredentials(),
                           &port);
  builder.RegisterService(&service);
  std::unique_ptr<grpc::Server> server(builder.BuildAndStart());
  std::cout << "Fake-Device-Port: " << port << std::endl;

  // Main event loop
  std::unique_lock<std::mutex> lock(edt_mutex);
  while (running) {
    condition.wait(lock);
    for (int i = 0; i < events.size(); i++) {
      events[i]();
    }
    events.clear();
  }
  server->Wait();
}