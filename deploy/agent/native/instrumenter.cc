#include "instrumenter.h"

#include "utils/log.h"

namespace swapper {

namespace {
static unordered_map<string, Transform*> transforms;
}

void AddTransform(const string& class_name, Transform* transform){
  transforms[class_name] = transform;
}

const unordered_map<string, Transform*>& GetTransforms() {
  return transforms;
}

// The agent never truly "exits", so we need to take extra care to free memory.
void DeleteTransforms() {
  for (auto& transform : transforms) {
    delete transform.second;
  }
}

void TransformClass(jvmtiEnv* jvmti, const char* class_name, int class_data_len,
                    const unsigned char* class_data, int*& new_class_data_len,
                    unsigned char**& new_class_data) {
  auto iter = GetTransforms().find(class_name);
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

}  // namespace swapper