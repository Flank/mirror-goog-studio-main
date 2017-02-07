/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "dex_ir.h"
#include "chronometer.h"
#include "dex_utf8.h"
#include "stats.h"

#include <algorithm>
#include <cstdint>
#include <map>
#include <memory>
#include <vector>
#include <sstream>

namespace ir {

// Returns the human-readable name for a primitive type
static const char* PrimitiveTypeName(char typeChar) {
  switch (typeChar) {
    case 'B': return "byte";
    case 'C': return "char";
    case 'D': return "double";
    case 'F': return "float";
    case 'I': return "int";
    case 'J': return "long";
    case 'S': return "short";
    case 'V': return "void";
    case 'Z': return "boolean";
  }
  CHECK(!"unexpected type");
  return nullptr;
}

// Converts a type descriptor to human-readable "dotted" form.  For
// example, "Ljava/lang/String;" becomes "java.lang.String", and
// "[I" becomes "int[]".
static std::string DescriptorToDecl(const char* desc) {
  std::stringstream ss;

  int array_dimensions = 0;
  while (*desc == '[') {
    ++array_dimensions;
    ++desc;
  }

  if (*desc == 'L') {
    for (++desc; *desc != ';'; ++desc) {
      CHECK(*desc != '\0');
      ss << (*desc == '/' ? '.' : *desc);
    }
  } else {
    ss << PrimitiveTypeName(*desc);
  }

  CHECK(desc[1] == '\0');

  // add the array brackets
  for (int i = 0; i < array_dimensions; ++i) {
    ss << "[]";
  }

  return ss.str();
}

// Human-readable type declaration
std::string Type::Decl() const {
  return DescriptorToDecl(descriptor->c_str());
}

// Helper for IR normalization
// (it sorts items and update the numeric idexes to match)
template <class T, class C>
static void IndexItems(std::vector<T>& items, C comp) {
  std::sort(items.begin(), items.end(), comp);
  for (size_t i = 0; i < items.size(); ++i) {
    items[i]->index = i;
  }
}

// Helper for IR normalization (DFS for topological sort)
void DexFile::TopSortClassIndex(Class* irClass, dex::u4* nextIndex) {
  if (irClass->index == dex::u4(-1)) {
    if (irClass->super_class && irClass->super_class->class_def) {
      TopSortClassIndex(irClass->super_class->class_def, nextIndex);
    }

    if (irClass->interfaces) {
      for (Type* interfaceType : irClass->interfaces->types) {
        if (interfaceType->class_def) {
          TopSortClassIndex(interfaceType->class_def, nextIndex);
        }
      }
    }

    CHECK(*nextIndex < classes.size());
    irClass->index = (*nextIndex)++;
  }
}

// Helper for IR normalization
// (topological sort the classes)
void DexFile::SortClassIndexes() {
  for (auto& irClass : classes) {
    irClass->index = dex::u4(-1);
  }

  dex::u4 nextIndex = 0;
  for (auto& irClass : classes) {
    TopSortClassIndex(irClass.get(), &nextIndex);
  }
}

// Helper for NormalizeClass()
static void SortEncodedFields(std::vector<EncodedField*>* fields) {
  std::sort(fields->begin(), fields->end(),
            [](const EncodedField* a, const EncodedField* b) {
              CHECK(a->field->index != b->field->index);
              return a->field->index < b->field->index;
            });
}

// Helper for NormalizeClass()
static void SortEncodedMethods(std::vector<EncodedMethod*>* methods) {
  std::sort(methods->begin(), methods->end(),
            [](const EncodedMethod* a, const EncodedMethod* b) {
              CHECK(a->method->index != b->method->index);
              return a->method->index < b->method->index;
            });
}

// Helper for IR normalization
// (sort the field & method arrays)
static void NormalizeClass(Class* irClass) {
  SortEncodedFields(&irClass->static_fields);
  SortEncodedFields(&irClass->instance_fields);
  SortEncodedMethods(&irClass->direct_methods);
  SortEncodedMethods(&irClass->virtual_methods);
}

// prepare the IR for generating a .dex image
// (the .dex format requires a specific sort order for some of the arrays, etc...)
//
// TODO: not a great solution - move this logic to the writer!
//
// TODO: the comparison predicate can be better expressed by using std::tie()
//  Ex. FieldDecl has a method comp() returning tie(parent->index, name->index, type->index)
//
void DexFile::Normalize() {
  slicer::Chronometer chrono(slicer::perf.norm_time);

  // sort build the .dex indexes
  IndexItems(strings, [](const own<String>& a, const own<String>& b) {
    // this list must be sorted by std::string contents, using UTF-16 code point values
    // (not in a locale-sensitive manner)
    return dex::Utf8Cmp(a->c_str(), b->c_str()) < 0;
  });

  IndexItems(types, [](const own<Type>& a, const own<Type>& b) {
    // this list must be sorted by string_id index
    return a->descriptor->index < b->descriptor->index;
  });

  IndexItems(protos, [](const own<Proto>& a, const own<Proto>& b) {
    // this list must be sorted in return-type (by type_id index) major order,
    // and then by argument list (lexicographic ordering, individual arguments
    // ordered by type_id index)
    if (a->return_type->index != b->return_type->index) {
      return a->return_type->index < b->return_type->index;
    } else {
      std::vector<Type*> empty;
      const auto& aParamTypes = a->param_types ? a->param_types->types : empty;
      const auto& bParamTypes = b->param_types ? b->param_types->types : empty;
      return std::lexicographical_compare(
          aParamTypes.begin(), aParamTypes.end(), bParamTypes.begin(),
          bParamTypes.end(),
          [](const Type* t1, const Type* t2) { return t1->index < t2->index; });
    }
  });

  IndexItems(fields, [](const own<FieldDecl>& a, const own<FieldDecl>& b) {
    // this list must be sorted, where the defining type (by type_id index) is
    // the major order, field name (by string_id index) is the intermediate
    // order, and type (by type_id index) is the minor order
    return (a->parent->index != b->parent->index)
               ? a->parent->index < b->parent->index
               : (a->name->index != b->name->index)
                     ? a->name->index < b->name->index
                     : a->type->index < b->type->index;
  });

  IndexItems(methods, [](const own<MethodDecl>& a, const own<MethodDecl>& b) {
    // this list must be sorted, where the defining type (by type_id index) is
    // the major order, method name (by string_id index) is the intermediate
    // order, and method prototype (by proto_id index) is the minor order
    return (a->parent->index != b->parent->index)
               ? a->parent->index < b->parent->index
               : (a->name->index != b->name->index)
                     ? a->name->index < b->name->index
                     : a->prototype->index < b->prototype->index;
  });

  // reverse topological sort
  //
  // the classes must be ordered such that a given class's superclass and
  // implemented interfaces appear in the list earlier than the referring
  // class
  //
  // CONSIDER: for a strict BCI scenario we can avoid this
  //
  SortClassIndexes();

  IndexItems(classes, [&](const own<Class>& a, const own<Class>& b) {
    CHECK(a->index < classes.size());
    CHECK(b->index < classes.size());
    CHECK(a->index != b->index);
    return a->index < b->index;
  });

  // normalize class data
  for (const auto& irClass : classes) {
    NormalizeClass(irClass.get());
  }

  // normalize annotations
  for (const auto& irAnnotation : annotations) {
    // elements must be sorted in increasing order by string_id index
    auto& elements = irAnnotation->elements;
    std::sort(elements.begin(), elements.end(),
              [](const AnnotationElement* a, const AnnotationElement* b) {
                return a->name->index < b->name->index;
              });
  }

  // normalize "annotation_set_item"
  for (const auto& irAnnotationSet : annotation_sets) {
    // The elements must be sorted in increasing order, by type_idx
    auto& annotations = irAnnotationSet->annotations;
    std::sort(annotations.begin(), annotations.end(),
              [](const Annotation* a, const Annotation* b) {
                return a->type->index < b->type->index;
              });
  }

  // normalize "annotations_directory_item"
  for (const auto& irAnnotationDirectory : annotations_directories) {
    // field_annotations: The elements of the list must be
    // sorted in increasing order, by field_idx
    auto& field_annotations = irAnnotationDirectory->field_annotations;
    std::sort(field_annotations.begin(), field_annotations.end(),
              [](const FieldAnnotation* a, const FieldAnnotation* b) {
                return a->field->index < b->field->index;
              });

    // method_annotations: The elements of the list must be
    // sorted in increasing order, by method_idx
    auto& method_annotations = irAnnotationDirectory->method_annotations;
    std::sort(method_annotations.begin(), method_annotations.end(),
              [](const MethodAnnotation* a, const MethodAnnotation* b) {
                return a->method->index < b->method->index;
              });

    // parameter_annotations: The elements of the list must be
    // sorted in increasing order, by method_idx
    auto& param_annotations = irAnnotationDirectory->param_annotations;
    std::sort(param_annotations.begin(), param_annotations.end(),
              [](const ParamAnnotation* a, const ParamAnnotation* b) {
                return a->method->index < b->method->index;
              });
  }
}

} // namespace ir

