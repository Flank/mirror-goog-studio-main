#include "jni.h"

#include <memory>
#include <string>
#include <vector>

namespace jniutils {
struct StackTrace {
  std::string msg;
  std::vector<std::string> frames;
  std::unique_ptr<StackTrace> cause;
};

/**
 * Gets the exception from JNI and clears it. Then constructs the stacktrace and
 * returns it as a jniutils::StackTrace.
 */
std::unique_ptr<StackTrace> getExceptionStackTrace(JNIEnv* jni);

/**
 * Prints the stack trace passed as argument into a string.
 */
std::string stackTraceToString(std::unique_ptr<StackTrace> stackTrace);
}  // namespace jniutils
