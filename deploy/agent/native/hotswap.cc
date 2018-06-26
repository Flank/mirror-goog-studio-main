#include <dirent.h>
#include <stdio.h>
#include <cstring>
#include <fstream>
#include <iostream>

#include "hotswap.h"
#include "jni.h"
#include "jvmti.h"
#include "utils/log.h"

using namespace std;

namespace swapper {

int EndsWith(const string& str, const string& suffix) {
  if (suffix.size() > str.size()) return false;
  return suffix == str.substr(str.size() - suffix.size());
}

int EndsWithDex(const string& str) { return EndsWith(str, ".dex"); }

size_t ReadFileBytes(const string& name, char** buffer_ptr) {
  ifstream fl(name);
  fl.seekg(0, ios::end);
  size_t len = fl.tellg();
  char* ret = (char*)malloc(len);
  fl.seekg(0, ios::beg);
  fl.read(ret, len);
  fl.close();
  *buffer_ptr = ret;
  return len;
}

jobject GetThreadClassLoader(JNIEnv* env) {
  jclass Class = env->FindClass("java/lang/Thread");
  jmethodID Method1 =
      env->GetStaticMethodID(Class, "currentThread", "()Ljava/lang/Thread;");
  jobject Thread = env->CallStaticObjectMethod(Class, Method1);
  jmethodID Method2 = env->GetMethodID(Class, "getContextClassLoader",
                                       "()Ljava/lang/ClassLoader;");
  jobject Loader = env->CallObjectMethod(Thread, Method2);
  return Loader;
}

bool HotSwap::RedefineClass(string& name, string& location) {
  jclass classLoaderClass = jni_->FindClass("java/lang/ClassLoader");
  jmethodID gFindClassMethod = jni_->GetMethodID(
      classLoaderClass, "findClass", "(Ljava/lang/String;)Ljava/lang/Class;");
  jobject gClassLoader = GetThreadClassLoader(jni_);
  jvmtiClassDefinition def;

  jclass klass = static_cast<jclass>(jni_->CallObjectMethod(
      gClassLoader, gFindClassMethod, jni_->NewStringUTF(name.c_str())));

  if (!klass) {
    jthrowable e = jni_->ExceptionOccurred();
    jni_->ExceptionClear();
    jclass clazz = jni_->GetObjectClass(e);
    jmethodID getMessage =
        jni_->GetMethodID(clazz, "getMessage", "()Ljava/lang/String;");
    jstring message = (jstring)jni_->CallObjectMethod(e, getMessage);
    const char* mstr = jni_->GetStringUTFChars(message, nullptr);
    Log::E("Exception calling find class on %s %s\n", name.c_str(), mstr);

    jni_->ExceptionClear();

    // Re-try with the normal class loader.
    klass = jni_->FindClass(name.c_str());
    if (!klass) {
      Log::E("Cannot find class %s for redefinition\n", name.c_str());
      return false;
    }
  }

  char* dex;
  def.klass = klass;
  def.class_byte_count = ReadFileBytes(location, &dex);
  def.class_bytes = (unsigned char*)dex;
  jvmtiError err;
  err = jvmti_->RedefineClasses(1, &def);
  free(dex);

  if (err == JVMTI_ERROR_UNMODIFIABLE_CLASS) {
    // TODO(acleung): Most likely an interface. Need to inform the caller later.
    return true;
  }

  // TODO(acleung): Inform caller of specific JVMTI Errors.
  if (err) {
    Log::E("Error RedefineClass %d %s\n", err, name.c_str());
    return false;
  } else {
    return true;
  }
}

bool HotSwap::DoHotSwap(string& dexdir) {
  bool success = true;
  const char* dexloc = dexdir.c_str();
  struct dirent* de;
  DIR* dr = opendir(dexloc);

  if (dr == nullptr) {
    Log::E("Could not open dex location %s", dexloc);
    return false;
  }

  while ((de = readdir(dr)) != nullptr) {
    if (EndsWithDex(de->d_name)) {
      string dex(de->d_name);
      // TODO(acleung): Investigate rather we want to redefine all the classes
      // in one go.
      string classname = dex.substr(0, dex.size() - 4);
      string dexloc = dexdir + dex;
      success = this->RedefineClass(classname, dexloc) && success;
    }
  }
  closedir(dr);
  Log::V("Done HotSwapping %s", dexloc);
  return success;
}

}  // namespace swapper
