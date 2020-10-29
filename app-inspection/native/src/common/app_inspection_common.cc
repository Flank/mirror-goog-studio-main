#include "app_inspection_common.h"

namespace app_inspection {

const char* ARTIFACT_COORDINATE_CLASS =
    "com/android/tools/agent/app/inspection/version/ArtifactCoordinate";
const std::string ARTIFACT_COORDINATE_TYPE =
    "L" + std::string(ARTIFACT_COORDINATE_CLASS) + ";";

jobject CreateArtifactCoordinate(JNIEnv* env, jstring group_id,
                                 jstring artifact_id, jstring version) {
  jclass clazz = env->FindClass(ARTIFACT_COORDINATE_CLASS);
  jmethodID constructor = env->GetMethodID(
      clazz, "<init>",
      "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
  return env->NewObject(clazz, constructor, group_id, artifact_id, version);
}

}  // namespace app_inspection
