#ifndef INSTALLER_COROUTINE_AGENT_H_
#define INSTALLER_COROUTINE_AGENT_H_

#include "tools/base/deploy/installer/command.h"
#include "tools/base/deploy/installer/workspace.h"

#include <string>

namespace deploy {

class InstallCoroutineAgentCommand : public Command {
 public:
  InstallCoroutineAgentCommand(Workspace& workspace) : Command(workspace) {}
  virtual ~InstallCoroutineAgentCommand() {}
  virtual void ParseParameters(const proto::InstallerRequest& request);
  virtual void Run(proto::InstallerResponse* response);

 private:
  proto::InstallCoroutineAgentRequest request_;
  std::string package_name_;
};

}  // namespace deploy

#endif  // INSTALLER_COROUTINE_AGENT_H_
