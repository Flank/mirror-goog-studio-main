#include <jni.h>

#include <memory>
#include <optional>
#include <sstream>
#include <string>

#include "android/log.h"
#include "curl/curl.h"
#include "json/json.h"

using namespace std::string_literals;

#define DISALLOW_COPY_AND_ASSIGN(TypeName) \
  TypeName(const TypeName&) = delete;      \
  void operator=(const TypeName&) = delete

#define LOG_TAG "PrefabAARTest"

#define LOG_INFO(...) \
  __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOG_ERROR(...) \
  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

class CurlGlobalState {
 public:
  CurlGlobalState() { curl_global_init(CURL_GLOBAL_DEFAULT); }

  ~CurlGlobalState() { curl_global_cleanup(); }

  DISALLOW_COPY_AND_ASSIGN(CurlGlobalState);
};

static std::string buffer;
size_t write_fn(char* data, size_t size, size_t nmemb, void* user_data) {
  LOG_INFO("Writing %zu, %zu", size, nmemb);
  buffer.append(data, size * nmemb);
  return size * nmemb;
}

static std::optional<std::string> get(const std::string& cacert_path,
                                      const std::string& url,
                                      std::string* error) {
  std::string placeholder;
  if (error == nullptr) {
    error = &placeholder;
  }

  std::unique_ptr<CURL, decltype(&curl_easy_cleanup)> curl(curl_easy_init(),
                                                           curl_easy_cleanup);
  if (curl == nullptr) {
    *error = "Failed to create CURL object";
    return std::nullopt;
  }

  CURLcode res = curl_easy_setopt(curl.get(), CURLOPT_URL, url.c_str());
  if (res != CURLE_OK) {
    *error = "CURLOPT_URL failed: "s + curl_easy_strerror(res);
    return std::nullopt;
  }

  res = curl_easy_setopt(curl.get(), CURLOPT_VERBOSE, 1L);
  if (res != CURLE_OK) {
    *error = "CURLOPT_VERBOSE failed: "s + curl_easy_strerror(res);
    return std::nullopt;
  }

  res = curl_easy_setopt(curl.get(), CURLOPT_CAINFO, cacert_path.c_str());
  if (res != CURLE_OK) {
    *error = "CURLOPT_VERBOSE failed: "s + curl_easy_strerror(res);
    return std::nullopt;
  }

  res = curl_easy_setopt(curl.get(), CURLOPT_WRITEFUNCTION, write_fn);
  if (res != CURLE_OK) {
    *error = "CURLOPT_WRITEFUNCTION failed: "s + curl_easy_strerror(res);
    return std::nullopt;
  }

  res = curl_easy_setopt(curl.get(), CURLOPT_FOLLOWLOCATION, 1L);
  if (res != CURLE_OK) {
    *error = "CURLOPT_FOLLOWLOCATION failed: "s + curl_easy_strerror(res);
    return std::nullopt;
  }

  res = curl_easy_perform(curl.get());
  if (res != CURLE_OK) {
    *error = "easy_perform failed: "s + curl_easy_strerror(res);
    return std::nullopt;
  }

  return std::string(buffer);
}

static std::string get_app_text(const std::string& cacert_path) {
  CurlGlobalState curl_global_state;
  std::string error;
  auto result =
      get(cacert_path,
          "http://android-review.googlesource.com/changes/?q=status:open&n=10",
          &error);
  if (!result) {
    return error.c_str();
  }

  // Strip XSSI defense prefix:
  // https://gerrit-review.googlesource.com/Documentation/rest-api.html#output
  LOG_INFO("Result is %zu bytes long", result->size());
  const std::string payload = result.value().substr(5);

  Json::Value root;
  std::istringstream(payload) >> root;
  std::ostringstream builder;
  for (const auto& change : root) {
    builder << change["subject"].asString() << std::endl;
  }
  return builder.str();
}

typedef std::unique_ptr<const char[], std::function<void(const char*)>>
    JniString;

std::string jstring_to_string(JNIEnv* env, jstring str) {
  JniString cstr(env->GetStringUTFChars(str, nullptr),
                 [=](const char* p) { env->ReleaseStringUTFChars(str, p); });

  if (cstr == nullptr) {
    LOG_ERROR("%s: GetStringUTFChars failed", __func__);
    abort();
  }

  const jsize len = env->GetStringUTFLength(str);
  return std::string(cstr.get(), len);
}

JNIEXPORT jstring JNICALL get_app_text_jni(JNIEnv* env, jobject /* this */,
                                           jstring cacert_java) {
  if (cacert_java == nullptr) {
    LOG_ERROR("cacert argument cannot be null");
    abort();
  }

  const std::string cacert = jstring_to_string(env, cacert_java);
  return env->NewStringUTF(get_app_text(cacert).c_str());
}

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
  JNIEnv* env;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
    LOG_ERROR("Could not get Java environment");
    return JNI_ERR;
  }

  // Get jclass with env->FindClass.
  // Register methods with env->RegisterNatives.

  static const JNINativeMethod methods[] = {
      {
          "stringFromJNI",
          "(Ljava/lang/String;)Ljava/lang/String;",
          reinterpret_cast<void*>(get_app_text_jni),
      },
  };

  const char mainClass[] = "com/android/prefabaartest/MainActivity";
  jclass c = env->FindClass(mainClass);
  if (c == nullptr) {
    LOG_ERROR("Could not find %s", mainClass);
    return JNI_ERR;
  }

  int rc = env->RegisterNatives(c, methods,
                                sizeof(methods) / sizeof(JNINativeMethod));
  if (rc != JNI_OK) {
    LOG_ERROR("Could not RegisterNatives");
    return rc;
  }

  return JNI_VERSION_1_6;
}
