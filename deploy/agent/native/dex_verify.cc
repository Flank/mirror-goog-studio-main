#include "tools/base/deploy/agent/native/dex_verify.h"

#include <mutex>
#include <unordered_map>
#include <unordered_set>

#include "slicer/dex_ir.h"
#include "slicer/reader.h"
#include "tools/base/deploy/agent/native/jni/jni_util.h"
#include "tools/base/deploy/common/event.h"
#include "tools/base/deploy/common/log.h"

namespace deploy {

namespace {
std::unordered_map<std::string, ClassInfo> classes_to_compare;
std::vector<proto::JvmtiErrorDetails>* errors;

// Guards classes_to_compare and errors.
std::mutex verify_mutex;

// Compares the IR for the new class definition with the IR for the currently
// loaded class definition. Adds incompatible changes to the errors vector.
void CompareClasses(const char* class_name, ir::Class* old_class,
                    ir::Class* new_class) {
  // Check static fields. We only care about name matching for now since we're
  // currently only using this verification for R inner classes.
  std::unordered_set<std::string> old_fields;
  for (auto& field : old_class->static_fields) {
    old_fields.emplace(field->decl->name->c_str());
  }

  // Checks for added fields by seeing which fields in new_class don't have a
  // match in old_class. Also sets up the check for deleted fields by pairing
  // off fields that exist in both the old and new class.
  std::unordered_set<std::string> added_fields;
  for (auto& field : new_class->static_fields) {
    const char* field_name = field->decl->name->c_str();
    auto iter = old_fields.find(field_name);
    if (iter != old_fields.end()) {
      old_fields.erase(iter);
    } else {
      proto::JvmtiErrorDetails error;
      error.set_type(proto::JvmtiErrorDetails::FIELD_ADDED);
      error.set_name(field_name);
      error.set_class_name(class_name);
      errors->emplace_back(std::move(error));
    }
  }

  // Checks for deleted fields by determining which fields in old_class didn't
  // have a corresponding match in new_class.
  for (std::string deleted_field : old_fields) {
    proto::JvmtiErrorDetails error;
    error.set_type(proto::JvmtiErrorDetails::FIELD_REMOVED);
    error.set_name(deleted_field);
    error.set_class_name(class_name);
    errors->emplace_back(std::move(error));
  }
}

extern "C" void JNICALL Agent_VerifyClassFileLoadHook(
    jvmtiEnv* jvmti, JNIEnv* jni, jclass klass, jobject loader,
    const char* name, jobject protection_domain, jint class_data_len,
    const unsigned char* class_data, jint* new_class_data_len,
    unsigned char** new_class_data) {
  std::lock_guard<std::mutex> lock(verify_mutex);

  auto iter = classes_to_compare.find(name);
  if (iter == classes_to_compare.end()) {
    return;
  }

  const std::string& class_name = "L" + iter->first + ";";
  ClassInfo& class_to_compare = iter->second;

  // TODO: Can we do this without building an IR? Does that speed this up?
  dex::Reader old_reader(class_data, class_data_len);
  auto class_index = old_reader.FindClassIndex(class_name.c_str());
  if (class_index == dex::kNoIndex) {
    return;
  }
  old_reader.CreateClassIr(class_index);

  dex::Reader new_reader(class_to_compare.class_data,
                         class_to_compare.class_data_len);
  new_reader.CreateFullIr();

  auto old_ir = old_reader.GetIr();
  auto new_ir = new_reader.GetIr();

  if (old_ir->classes.size() != 1) {
    Log::E("Dex verification failed; multiple classes in old dex ir");
  }

  if (new_ir->classes.size() != 1) {
    Log::E("Dex verification failed; multiple classes in new dex ir");
    return;
  }

  CompareClasses(name, old_ir->classes.front().get(),
                 new_ir->classes.front().get());
}

}  // namespace

void CheckForClassErrors(jvmtiEnv* jvmti,
                         const std::vector<ClassInfo>& class_list,
                         std::vector<proto::JvmtiErrorDetails>* error_details) {
  Phase p("verifyClasses");
  if (class_list.empty()) {
    return;
  }

  std::vector<jclass> klasses;

  // We hold this lock to initialize the class_to_verify and error vectors, then
  // release the lock before we call RetransformClasses, as the
  // ClassFileLoadHook callback will hold the lock to modify the error vector.
  {
    std::lock_guard<std::mutex> lock(verify_mutex);
    for (auto& verify : class_list) {
      classes_to_compare.insert({verify.class_name, verify});
      klasses.push_back(verify.klass);
    }

    // Use the passed-in vector to accumulate verification results.
    errors = error_details;
  }

  jvmtiEventCallbacks callbacks;
  callbacks.ClassFileLoadHook = Agent_VerifyClassFileLoadHook;

  CheckJvmti(jvmti->SetEventCallbacks(&callbacks, sizeof(jvmtiEventCallbacks)),
             "Error setting event callbacks for dex verification.");

  CheckJvmti(jvmti->SetEventNotificationMode(
                 JVMTI_ENABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, NULL),
             "Could not enable class file load hook event");

  // The call to RetransformClasses causes the VM to re-issue the
  // ClassFileLoadHook event for each of the classes specified, which allows the
  // callback to have access to the currently loaded bytecode for that class.
  // However, we do not actually use the callback to perform a class
  // redefinition or retransformation.
  CheckJvmti(jvmti->RetransformClasses(klasses.size(), klasses.data()),
             "Could not retransform classes");

  CheckJvmti(jvmti->SetEventNotificationMode(
                 JVMTI_DISABLE, JVMTI_EVENT_CLASS_FILE_LOAD_HOOK, NULL),
             "Could not disable class file load hook event");

  // We do this to clean up after ourselves. Not a huge deal if this fails.
  CheckJvmti(jvmti->SetEventCallbacks(nullptr, 0),
             "Error clearing event callbacks after dex verification.");

  // Lock again while we clear the global collections.
  {
    std::lock_guard<std::mutex> lock(verify_mutex);
    classes_to_compare.clear();
    errors = nullptr;
  }
}

}  // namespace deploy