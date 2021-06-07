#include "jni_utils.h"

namespace jniutils {
static std::unique_ptr<StackTrace> retrieveStackTrace(
    JNIEnv* jni, jthrowable exception, jmethodID throwable_getCause,
    jmethodID throwable_getStackTrace, jmethodID throwable_getMessage,
    jmethodID frame_toString) {
  std::unique_ptr<StackTrace> stackTrace(new StackTrace());

  // get the array of StackTraceElements
  jobjectArray frames =
      (jobjectArray)jni->CallObjectMethod(exception, throwable_getStackTrace);

  if (frames == nullptr) {
    return nullptr;
  }

  jsize frames_length = jni->GetArrayLength(frames);

  // add Throwable.getMessage() before descending stack trace messages
  jstring msg_obj =
      (jstring)jni->CallObjectMethod(exception, throwable_getMessage);

  stackTrace->msg = jni->GetStringUTFChars(msg_obj, 0);

  jni->DeleteLocalRef(msg_obj);

  for (jsize i = 0; i < frames_length; i++) {
    // Get the string returned from the 'toString()'
    // method of the next frame and append it to
    // the error message.
    jobject frame = jni->GetObjectArrayElement(frames, i);
    jstring frame_str = (jstring)jni->CallObjectMethod(frame, frame_toString);

    const char* frame_str_utf = jni->GetStringUTFChars(frame_str, 0);
    stackTrace->frames.emplace_back(frame_str_utf);

    jni->ReleaseStringUTFChars(frame_str, frame_str_utf);
    jni->DeleteLocalRef(frame_str);
    jni->DeleteLocalRef(frame);
  }

  // if 'exception' has a cause then append the stack trace messages from the
  // cause
  jthrowable cause =
      (jthrowable)jni->CallObjectMethod(exception, throwable_getCause);
  if (cause != nullptr) {
    stackTrace->cause = retrieveStackTrace(
        jni, cause, throwable_getCause, throwable_getStackTrace,
        throwable_getMessage, frame_toString);
  }

  return stackTrace;
}

std::unique_ptr<StackTrace> getExceptionStackTrace(JNIEnv* jni) {
  // get the exception and clear, as no JNI calls can be made while an exception
  // exists
  jthrowable exception = jni->ExceptionOccurred();
  jni->ExceptionClear();

  static jclass throwable_class = jni->FindClass("java/lang/Throwable");
  static jmethodID throwable_getCause =
      jni->GetMethodID(throwable_class, "getCause", "()Ljava/lang/Throwable;");
  static jmethodID throwable_getStackTrace = jni->GetMethodID(
      throwable_class, "getStackTrace", "()[Ljava/lang/StackTraceElement;");
  static jmethodID throwable_getMessage =
      jni->GetMethodID(throwable_class, "getMessage", "()Ljava/lang/String;");

  static jclass frame_class = jni->FindClass("java/lang/StackTraceElement");
  static jmethodID frame_toString =
      jni->GetMethodID(frame_class, "toString", "()Ljava/lang/String;");

  std::unique_ptr<StackTrace> stackTrace = retrieveStackTrace(
      jni, exception, throwable_getCause, throwable_getStackTrace,
      throwable_getMessage, frame_toString);

  return stackTrace;
}

std::string stackTraceToString(std::unique_ptr<StackTrace> stackTrace) {
  std::string stringStackTrace;
  stringStackTrace += "Exception: " + stackTrace->msg + "\n";

  for (auto const& frame : stackTrace->frames) {
    stringStackTrace += "   at " + frame;
    stringStackTrace += "\n";
  }

  if (stackTrace->cause != nullptr) {
    stringStackTrace += "Caused by:\n";
    stringStackTrace += stackTraceToString(move(stackTrace->cause));
  }

  return stringStackTrace;
}
}  // namespace jniutils
