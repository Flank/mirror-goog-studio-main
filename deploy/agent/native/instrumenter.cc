#include "instrumenter.h"

#include "jni.h"
#include "jvmti.h"

#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>
#include <string>

#include "jni/jni_class.h"
#include "native_callbacks.h"
#include "utils/log.h"

#include "instrumentation.jar.cc"

namespace deploy {

namespace {
const char* kBreadcrumbClass = "com/android/tools/deploy/instrument/Breadcrumb";
const char* kHandlerWrapperClass =
    "com/android/tools/deploy/instrument/ActivityThreadHandlerWrapper";

const std::string kInstrumentationJarName =
    std::string("instruments-") + instrumentation_jar_hash + ".jar";

static unordered_map<string, Transform*> transforms;

#define FILE_MODE (S_IRUSR | S_IWUSR)

std::string GetInstrumentJarPath(const std::string& package_name) {
#ifdef __ANDROID__
  std::string target_jar_dir_ =
      std::string("/data/data/") + package_name + "/.studio/";
  std::string target_jar = target_jar_dir_ + kInstrumentationJarName;
  return target_jar;
#else
  // For tests purposes.
  char* tmp_dir = getenv("TEST_TMPDIR");
  Log::E("GetInstrumentPath:%s", tmp_dir);
  if (tmp_dir == nullptr) {
    return kInstrumentationJarName;
  } else {
    return std::string(tmp_dir) + "/" + kInstrumentationJarName;
  }
#endif
}

// Check if the jar_path exists. If it doesn't, generate its content using the
// jar embedded in the .data section of this executable.
// TODO: Don't write to disk. Have jvmti load the jar directly from a memory
// mapped fd to agent.so.
bool WriteJarToDiskIfNecessary(const std::string& jar_path) {
  // If file exists, there is no need to do anything.
  if (access(jar_path.c_str(), F_OK) != -1) {
    return true;
  }

  // TODO: Would be more efficient to have the offet and size and use sendfile()
  // to avoid a userland trip.
  int fd = open(jar_path.c_str(), O_WRONLY | O_CREAT, FILE_MODE);
  if (fd == -1) {
    Log::E("WriteJarToDiskIfNecessary(). Unable to open().");
    return false;
  }
  int written = write(fd, instrumentation_jar, instrumentation_jar_len);
  if (written == -1) {
    Log::E("WriteJarToDiskIfNecessary(). Unable to write().");
    return false;
  }

  int closeResult = close(fd);
  if (closeResult == -1) {
    Log::E("WriteJarToDiskIfNecessary(). Unable to close().");
    return false;
  }

  return true;
}

bool LoadInstrumentationJar(jvmtiEnv* jvmti, JNIEnv* jni,
                            const std::string& jar_path) {
  // Check for the existence of a breadcrumb class, indicating a previous agent
  // has already loaded instrumentation. If no previous agent has run on this
  // jvm, add our instrumentation classes to the bootstrap class loader.
  jclass unused = jni->FindClass(kBreadcrumbClass);
  if (unused == nullptr) {
    Log::V("No existing instrumentation found. Loading instrumentation from %s",
           kInstrumentationJarName.c_str());
    jni->ExceptionClear();
    if (jvmti->AddToBootstrapClassLoaderSearch(jar_path.c_str()) !=
        JVMTI_ERROR_NONE) {
      return false;
    }
  } else {
    jni->DeleteLocalRef(unused);
  }
  return true;
}

bool Instrument(jvmtiEnv* jvmti, JNIEnv* jni, const std::string& jar) {
  // The breadcrumb class stores some checks between runs of the agent.
  // We can't use the class from the FindClass call because it may not have
  // actually found the class.
  JniClass breadcrumb(jni, kBreadcrumbClass);

  // Ensure that the jar hasn't changed since we last instrumented. If it has,
  // fail out for now. This is an important scenario to guard against, since it
  // would likely cause silent failures.
  jvalue jar_hash = {.l = jni->NewStringUTF(instrumentation_jar_hash)};
  jboolean matches = breadcrumb.CallStaticMethod<jboolean>(
      {"checkHash", "(Ljava/lang/String;)Z"}, &jar_hash);
  jni->DeleteLocalRef(jar_hash.l);

  if (!matches) {
    Log::E(
        "The instrumentation jar at %s does not match the jar previously used "
        "to instrument. The application must be restarted.",
        kInstrumentationJarName.c_str());
    return false;
  }

  // Check if we need to instrument, or if a previous agent successfully did.
  if (!breadcrumb.CallStaticMethod<jboolean>(
          {"isFinishedInstrumenting", "()Z"})) {
    // Instrument the activity thread handler using RetransformClasses.
    // TODO: If we instrument more, make this more general.

    AddTransform("android/app/ActivityThread$H",
                 new ActivityThreadHandlerTransform());

    jclass activity_thread_h = jni->FindClass("android/app/ActivityThread$H");
    if (jni->ExceptionCheck()) {
      Log::E("Could not find activity thread handler");
      jni->ExceptionClear();
      return false;
    }

    jvmti->SetEventNotificationMode(JVMTI_ENABLE,
                                    JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, NULL);
    jvmti->RetransformClasses(1, &activity_thread_h);
    jvmti->SetEventNotificationMode(JVMTI_DISABLE,
                                    JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, NULL);

    jni->DeleteLocalRef(activity_thread_h);

    DeleteTransforms();

    // Mark that we've finished instrumentation.
    breadcrumb.CallStaticMethod<void>({"setFinishedInstrumenting", "()V"});
    Log::V("Finished instrumenting");
  }

  return true;
}
}  // namespace

void AddTransform(const string& class_name, Transform* transform) {
  transforms[class_name] = transform;
}

const unordered_map<string, Transform*>& GetTransforms() { return transforms; }

// The agent never truly "exits", so we need to take extra care to free memory.
void DeleteTransforms() {
  for (auto& transform : transforms) {
    delete transform.second;
  }
}

// Event that fires when the agent loads a class file.
extern "C" void JNICALL Agent_ClassFileLoadHook(
    jvmtiEnv* jvmti, JNIEnv* jni, jclass class_being_redefined, jobject loader,
    const char* name, jobject protection_domain, jint class_data_len,
    const unsigned char* class_data, jint* new_class_data_len,
    unsigned char** new_class_data) {
  auto iter = GetTransforms().find(name);
  if (iter == GetTransforms().end()) {
    return;
  }

  // The class name needs to be in JNI-format.
  string descriptor = "L" + iter->first + ";";

  dex::Reader reader(class_data, class_data_len);
  auto class_index = reader.FindClassIndex(descriptor.c_str());
  if (class_index == dex::kNoIndex) {
    // TODO: Handle failure.
    return;
  }

  reader.CreateClassIr(class_index);
  auto dex_ir = reader.GetIr();
  iter->second->Apply(dex_ir);

  size_t new_image_size = 0;
  dex::u1* new_image = nullptr;
  dex::Writer writer(dex_ir);

  JvmtiAllocator allocator(jvmti);
  new_image = writer.CreateImage(&allocator, &new_image_size);

  *new_class_data_len = new_image_size;
  *new_class_data = new_image;
}

bool InstrumentApplication(jvmtiEnv* jvmti, JNIEnv* jni,
                           const std::string& package_name) {
  jvmtiEventCallbacks callbacks;
  callbacks.ClassFileLoadHook = Agent_ClassFileLoadHook;

  if (jvmti->SetEventCallbacks(&callbacks, sizeof(jvmtiEventCallbacks)) !=
      JVMTI_ERROR_NONE) {
    Log::E("Error setting event callbacks.");
    return false;
  }

  std::string instrument_jar_path = GetInstrumentJarPath(package_name);

  // Make sure the instrumentation jar is ready on disk.
  if (!WriteJarToDiskIfNecessary(instrument_jar_path)) {
    Log::E("Error writing instrumentation.jar to disk.");
    return false;
  }

  if (!LoadInstrumentationJar(jvmti, jni, instrument_jar_path)) {
    Log::E("Error loading instrumentation dex.");
    return false;
  }

  if (!Instrument(jvmti, jni, instrument_jar_path)) {
    Log::E("Error instrumenting application.");
    return false;
  }

  vector<NativeBinding> native_bindings;
  native_bindings.emplace_back(kHandlerWrapperClass,
                               "getApplicationInfoChangedValue", "()I",
                               (void*)&Native_GetAppInfoChanged);
  native_bindings.emplace_back(kHandlerWrapperClass, "tryRedefineClasses",
                               "()Z", (void*)&Native_TryRedefineClasses);

  // Need to register native methods every time; otherwise, the Java methods
  // could potentially call old versions if a previous agent.so was loaded.
  RegisterNatives(jni, native_bindings);

  // Enable hot-swapping via the callback.
  JniClass handlerWrapper(jni, kHandlerWrapperClass);
  handlerWrapper.CallStaticMethod<void>({"prepareForHotSwap", "()V"});

  return true;
}

}  // namespace deploy