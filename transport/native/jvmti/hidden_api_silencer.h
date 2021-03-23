#ifndef HIDDEN_API_POLICY_H
#define HIDDEN_API_POLICY_H

#include <jvmti.h>

namespace profiler {

class HiddenApiSilencer {
 public:
  explicit HiddenApiSilencer(jvmtiEnv* jvmti);
  ~HiddenApiSilencer();

 private:
  jint policy_ = 0;
  jvmtiEnv* jvmti_ = nullptr;
  bool supported_ = false;

  bool Setup();

  jvmtiExtensionFunction DisableHiddenApiEnforcementPolicy = nullptr;
  jvmtiExtensionFunction GetHiddenApiEnforcementPolicy = nullptr;
  jvmtiExtensionFunction SetHiddenApiEnforcementPolicy = nullptr;

  void Free(void* obj);
};

}  // namespace profiler
#endif
