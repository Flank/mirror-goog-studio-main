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

#include "reader.h"
#include "dex_bytecode.h"
#include "chronometer.h"
#include "dex_leb128.h"

#include <assert.h>
#include <string.h>
#include <type_traits>
#include <cstdlib>

namespace dex {

Reader::Reader(const dex::u1* image, size_t size) : image_(image), size_(size) {
  // init the header reference
  header_ = ptr<dex::Header>(0);
  ValidateHeader();

  // start with an "empty" .dex IR
  dex_ir_ = std::make_shared<ir::DexFile>();
  dex_ir_->magic = slicer::MemView(header_, sizeof(dex::Header::magic));
}

slicer::ArrayView<const dex::ClassDef> Reader::ClassDefs() const {
  return section<dex::ClassDef>(header_->class_defs_off,
                                header_->class_defs_size);
}

slicer::ArrayView<const dex::StringId> Reader::StringIds() const {
  return section<dex::StringId>(header_->string_ids_off,
                                header_->string_ids_size);
}

slicer::ArrayView<const dex::TypeId> Reader::TypeIds() const {
  return section<dex::TypeId>(header_->type_ids_off,
                              header_->type_ids_size);
}

slicer::ArrayView<const dex::FieldId> Reader::FieldIds() const {
  return section<dex::FieldId>(header_->field_ids_off,
                               header_->field_ids_size);
}

slicer::ArrayView<const dex::MethodId> Reader::MethodIds() const {
  return section<dex::MethodId>(header_->method_ids_off,
                                header_->method_ids_size);
}

slicer::ArrayView<const dex::ProtoId> Reader::ProtoIds() const {
  return section<dex::ProtoId>(header_->proto_ids_off,
                               header_->proto_ids_size);
}

const dex::MapList* Reader::DexMapList() const {
  return dataPtr<dex::MapList>(header_->map_off);
}

const char* Reader::GetStringMUTF8(dex::u4 index) const {
  if (index == dex::kNoIndex) {
    return "<no_string>";
  }
  const dex::u1* strData = GetStringData(index);
  dex::ReadULeb128(&strData);
  return reinterpret_cast<const char*>(strData);
}

void Reader::CreateFullIr() {
  size_t classCount = ClassDefs().size();
  for (size_t i = 0; i < classCount; ++i) {
    CreateClassIr(i);
  }
}

void Reader::CreateClassIr(dex::u4 index) {
  auto ir_class = GetClass(index);
  CHECK(ir_class != nullptr);
}

// Returns the index of the class with the specified
// descriptor, or kNoIndex if not found
dex::u4 Reader::FindClassIndex(const char* class_descriptor) const {
  auto classes = ClassDefs();
  auto types = TypeIds();
  for (dex::u4 i = 0; i < classes.size(); ++i) {
    auto typeId = types[classes[i].class_idx];
    const char* descriptor = GetStringMUTF8(typeId.descriptor_idx);
    if (strcmp(class_descriptor, descriptor) == 0) {
      return i;
    }
  }
  return dex::kNoIndex;
}

// map a .dex index to corresponding .dex IR node
//
// NOTES:
//  1. the mapping beween an index and the indexed
//     .dex IR nodes is 1:1
//  2. we do a single index lookup for both existing
//     nodes as well as new nodes
//  3. dummy is an invalid, but non-null pointer value
//     used to check that the mapping loookup/update is atomic
//  4. there should be no recursion with the same index
//     (we use the dummy value to guard against this too)
//
ir::Class* Reader::GetClass(dex::u4 index) {
  CHECK(index != dex::kNoIndex);
  auto& p = dex_ir_->classes_map[index];
  auto dummy = reinterpret_cast<ir::Class*>(1);
  if (p == nullptr) {
    p = dummy;
    auto newClass = ParseClass(index);
    CHECK(p == dummy);
    p = newClass;
  }
  CHECK(p != dummy);
  return p;
}

// map a .dex index to corresponding .dex IR node
// (see the Reader::GetClass() comments)
ir::Type* Reader::GetType(dex::u4 index) {
  CHECK(index != dex::kNoIndex);
  auto& p = dex_ir_->types_map[index];
  auto dummy = reinterpret_cast<ir::Type*>(1);
  if (p == nullptr) {
    p = dummy;
    auto newType = ParseType(index);
    CHECK(p == dummy);
    p = newType;
  }
  CHECK(p != dummy);
  return p;
}

// map a .dex index to corresponding .dex IR node
// (see the Reader::GetClass() comments)
ir::FieldDecl* Reader::GetFieldDecl(dex::u4 index) {
  CHECK(index != dex::kNoIndex);
  auto& p = dex_ir_->fields_map[index];
  auto dummy = reinterpret_cast<ir::FieldDecl*>(1);
  if (p == nullptr) {
    p = dummy;
    auto newField = ParseFieldDecl(index);
    CHECK(p == dummy);
    p = newField;
  }
  CHECK(p != dummy);
  return p;
}

// map a .dex index to corresponding .dex IR node
// (see the Reader::GetClass() comments)
ir::MethodDecl* Reader::GetMethodDecl(dex::u4 index) {
  CHECK(index != dex::kNoIndex);
  auto& p = dex_ir_->methods_map[index];
  auto dummy = reinterpret_cast<ir::MethodDecl*>(1);
  if (p == nullptr) {
    p = dummy;
    auto newMethod = ParseMethodDecl(index);
    CHECK(p == dummy);
    p = newMethod;
  }
  CHECK(p != dummy);
  return p;
}

// map a .dex index to corresponding .dex IR node
// (see the Reader::GetClass() comments)
ir::Proto* Reader::GetProto(dex::u4 index) {
  CHECK(index != dex::kNoIndex);
  auto& p = dex_ir_->protos_map[index];
  auto dummy = reinterpret_cast<ir::Proto*>(1);
  if (p == nullptr) {
    p = dummy;
    auto newProto = ParseProto(index);
    CHECK(p == dummy);
    p = newProto;
  }
  CHECK(p != dummy);
  return p;
}

// map a .dex index to corresponding .dex IR node
// (see the Reader::GetClass() comments)
ir::String* Reader::GetString(dex::u4 index) {
  CHECK(index != dex::kNoIndex);
  auto& p = dex_ir_->strings_map[index];
  auto dummy = reinterpret_cast<ir::String*>(1);
  if (p == nullptr) {
    p = dummy;
    auto newString = ParseString(index);
    CHECK(p == dummy);
    p = newString;
  }
  CHECK(p != dummy);
  return p;
}

ir::Class* Reader::ParseClass(dex::u4 index) {
  auto& dexClassDef = ClassDefs()[index];
  auto ir_class = dex_ir_->Alloc<ir::Class>();

  ir_class->type = GetType(dexClassDef.class_idx);
  assert(ir_class->type->class_def == nullptr);
  ir_class->type->class_def = ir_class;

  ir_class->access_flags = dexClassDef.access_flags;
  ir_class->interfaces = ExtractTypeList(dexClassDef.interfaces_off);

  if (dexClassDef.superclass_idx != dex::kNoIndex) {
    ir_class->super_class = GetType(dexClassDef.superclass_idx);
  }

  if (dexClassDef.source_file_idx != dex::kNoIndex) {
    ir_class->source_file = GetString(dexClassDef.source_file_idx);
  }

  if (dexClassDef.class_data_off != 0) {
    const dex::u1* class_data = dataPtr<dex::u1>(dexClassDef.class_data_off);

    dex::u4 staticFieldsCount = dex::ReadULeb128(&class_data);
    dex::u4 instanceFieldsCount = dex::ReadULeb128(&class_data);
    dex::u4 directMethodsCount = dex::ReadULeb128(&class_data);
    dex::u4 virtualMethodsCount = dex::ReadULeb128(&class_data);

    dex::u4 baseIndex = dex::kNoIndex;
    for (dex::u4 i = 0; i < staticFieldsCount; ++i) {
      ir_class->static_fields.push_back(
          ParseEncodedField(&class_data, &baseIndex));
    }

    baseIndex = dex::kNoIndex;
    for (dex::u4 i = 0; i < instanceFieldsCount; ++i) {
      ir_class->instance_fields.push_back(
          ParseEncodedField(&class_data, &baseIndex));
    }

    baseIndex = dex::kNoIndex;
    for (dex::u4 i = 0; i < directMethodsCount; ++i) {
      auto method = ParseEncodedMethod(&class_data, &baseIndex);
      method->parent_class = ir_class;
      ir_class->direct_methods.push_back(method);
    }

    baseIndex = dex::kNoIndex;
    for (dex::u4 i = 0; i < virtualMethodsCount; ++i) {
      auto method = ParseEncodedMethod(&class_data, &baseIndex);
      method->parent_class = ir_class;
      ir_class->virtual_methods.push_back(method);
    }
  }

  ir_class->static_init = ExtractEncodedArray(dexClassDef.static_values_off);
  ir_class->annotations = ExtractAnnotations(dexClassDef.annotations_off);
  ir_class->orig_index = index;

  return ir_class;
}

ir::AnnotationsDirectory* Reader::ExtractAnnotations(dex::u4 offset) {
  if (offset == 0) {
    return nullptr;
  }

  CHECK(offset % 4 == 0);

  // first check if we already extracted the same "annotations_directory_item"
  auto& irAnnotations = annotations_directories_[offset];
  if (irAnnotations == nullptr) {
    irAnnotations = dex_ir_->Alloc<ir::AnnotationsDirectory>();

    auto dexAnnotations = dataPtr<dex::AnnotationsDirectoryItem>(offset);

    irAnnotations->class_annotation =
        ExtractAnnotationSet(dexAnnotations->class_annotations_off);

    const dex::u1* ptr = reinterpret_cast<const dex::u1*>(dexAnnotations + 1);

    for (dex::u4 i = 0; i < dexAnnotations->fields_size; ++i) {
      irAnnotations->field_annotations.push_back(ParseFieldAnnotation(&ptr));
    }

    for (dex::u4 i = 0; i < dexAnnotations->methods_size; ++i) {
      irAnnotations->method_annotations.push_back(ParseMethodAnnotation(&ptr));
    }

    for (dex::u4 i = 0; i < dexAnnotations->parameters_size; ++i) {
      irAnnotations->param_annotations.push_back(ParseParamAnnotation(&ptr));
    }
  }
  return irAnnotations;
}

ir::Annotation* Reader::ExtractAnnotationItem(dex::u4 offset) {
  CHECK(offset != 0);

  // first check if we already extracted the same "annotation_item"
  auto& irAnnotation = annotations_[offset];
  if (irAnnotation == nullptr) {
    auto dexAnnotationItem = dataPtr<dex::AnnotationItem>(offset);
    const dex::u1* ptr = dexAnnotationItem->annotation;
    irAnnotation = ParseAnnotation(&ptr);
    irAnnotation->visibility = dexAnnotationItem->visibility;
  }
  return irAnnotation;
}

ir::AnnotationSet* Reader::ExtractAnnotationSet(dex::u4 offset) {
  if (offset == 0) {
    return nullptr;
  }

  CHECK(offset % 4 == 0);

  // first check if we already extracted the same "annotation_set_item"
  auto& irAnnotationSet = annotation_sets_[offset];
  if (irAnnotationSet == nullptr) {
    irAnnotationSet = dex_ir_->Alloc<ir::AnnotationSet>();

    auto dexAnnotationSet = dataPtr<dex::AnnotationSetItem>(offset);
    for (dex::u4 i = 0; i < dexAnnotationSet->size; ++i) {
      auto irAnnotation = ExtractAnnotationItem(dexAnnotationSet->entries[i]);
      assert(irAnnotation != nullptr);
      irAnnotationSet->annotations.push_back(irAnnotation);
    }
  }
  return irAnnotationSet;
}

ir::AnnotationSetRefList* Reader::ExtractAnnotationSetRefList(dex::u4 offset) {
  CHECK(offset % 4 == 0);

  auto dexAnnotationSetRefList = dataPtr<dex::AnnotationSetRefList>(offset);
  auto irAnnotationSetRefList = dex_ir_->Alloc<ir::AnnotationSetRefList>();

  for (dex::u4 i = 0; i < dexAnnotationSetRefList->size; ++i) {
    dex::u4 entryOffset = dexAnnotationSetRefList->list[i].annotations_off;
    if (entryOffset != 0) {
      auto irAnnotationSet = ExtractAnnotationSet(entryOffset);
      CHECK(irAnnotationSet != nullptr);
      irAnnotationSetRefList->annotations.push_back(irAnnotationSet);
    }
  }

  return irAnnotationSetRefList;
}

ir::FieldAnnotation* Reader::ParseFieldAnnotation(const dex::u1** pptr) {
  auto dexFieldAnnotation = reinterpret_cast<const dex::FieldAnnotationsItem*>(*pptr);
  auto irFieldAnnotation = dex_ir_->Alloc<ir::FieldAnnotation>();

  irFieldAnnotation->field = GetFieldDecl(dexFieldAnnotation->field_idx);

  irFieldAnnotation->annotations =
      ExtractAnnotationSet(dexFieldAnnotation->annotations_off);
  CHECK(irFieldAnnotation->annotations != nullptr);

  *pptr += sizeof(dex::FieldAnnotationsItem);
  return irFieldAnnotation;
}

ir::MethodAnnotation* Reader::ParseMethodAnnotation(const dex::u1** pptr) {
  auto dexMethodAnnotation =
      reinterpret_cast<const dex::MethodAnnotationsItem*>(*pptr);
  auto irMethodAnnotation = dex_ir_->Alloc<ir::MethodAnnotation>();

  irMethodAnnotation->method = GetMethodDecl(dexMethodAnnotation->method_idx);

  irMethodAnnotation->annotations =
      ExtractAnnotationSet(dexMethodAnnotation->annotations_off);
  CHECK(irMethodAnnotation->annotations != nullptr);

  *pptr += sizeof(dex::MethodAnnotationsItem);
  return irMethodAnnotation;
}

ir::ParamAnnotation* Reader::ParseParamAnnotation(const dex::u1** pptr) {
  auto dexParamAnnotation =
      reinterpret_cast<const dex::ParameterAnnotationsItem*>(*pptr);
  auto irParamAnnotation = dex_ir_->Alloc<ir::ParamAnnotation>();

  irParamAnnotation->method = GetMethodDecl(dexParamAnnotation->method_idx);

  irParamAnnotation->annotations =
      ExtractAnnotationSetRefList(dexParamAnnotation->annotations_off);
  CHECK(irParamAnnotation->annotations != nullptr);

  *pptr += sizeof(dex::ParameterAnnotationsItem);
  return irParamAnnotation;
}

ir::EncodedField* Reader::ParseEncodedField(const dex::u1** pptr, dex::u4* baseIndex) {
  auto irEncodedField = dex_ir_->Alloc<ir::EncodedField>();

  auto fieldIndex = dex::ReadULeb128(pptr);
  CHECK(fieldIndex != dex::kNoIndex);
  if (*baseIndex != dex::kNoIndex) {
    CHECK(fieldIndex != 0);
    fieldIndex += *baseIndex;
  }
  *baseIndex = fieldIndex;

  irEncodedField->field = GetFieldDecl(fieldIndex);
  irEncodedField->access_flags = dex::ReadULeb128(pptr);

  return irEncodedField;
}

// Parse an encoded variable-length integer value
// (sign-extend signed types, zero-extend unsigned types)
template <class T>
static T ParseIntValue(const dex::u1** pptr, size_t size) {
  static_assert(std::is_integral<T>::value, "must be an integral type");

  CHECK(size > 0);
  CHECK(size <= sizeof(T));

  T value = 0;
  for (int i = 0; i < size; ++i) {
    value |= T(*(*pptr)++) << (i * 8);
  }

  // sign-extend?
  if (std::is_signed<T>::value) {
    size_t shift = (sizeof(T) - size) * 8;
    value = T(value << shift) >> shift;
  }

  return value;
}

// Parse an encoded variable-length floating point value
// (zero-extend to the right)
template <class T>
static T ParseFloatValue(const dex::u1** pptr, size_t size) {
  CHECK(size > 0);
  CHECK(size <= sizeof(T));

  T value = 0;
  int startByte = sizeof(T) - size;
  for (dex::u1* p = reinterpret_cast<dex::u1*>(&value) + startByte; size > 0;
       --size) {
    *p++ = *(*pptr)++;
  }
  return value;
}

ir::EncodedValue* Reader::ParseEncodedValue(const dex::u1** pptr) {
  auto irEncodedValue = dex_ir_->Alloc<ir::EncodedValue>();

  EXTRA(auto basePtr = *pptr);

  dex::u1 header = *(*pptr)++;
  dex::u1 type = header & dex::kEncodedValueTypeMask;
  dex::u1 arg = header >> dex::kEncodedValueArgShift;

  irEncodedValue->type = type;

  switch (type) {
    case dex::kEncodedByte:
      irEncodedValue->u.byte_value = ParseIntValue<int8_t>(pptr, arg + 1);
      break;

    case dex::kEncodedShort:
      irEncodedValue->u.short_value = ParseIntValue<int16_t>(pptr, arg + 1);
      break;

    case dex::kEncodedChar:
      irEncodedValue->u.char_value = ParseIntValue<uint16_t>(pptr, arg + 1);
      break;

    case dex::kEncodedInt:
      irEncodedValue->u.int_value = ParseIntValue<int32_t>(pptr, arg + 1);
      break;

    case dex::kEncodedLong:
      irEncodedValue->u.long_value = ParseIntValue<int64_t>(pptr, arg + 1);
      break;

    case dex::kEncodedFloat:
      irEncodedValue->u.float_value = ParseFloatValue<float>(pptr, arg + 1);
      break;

    case dex::kEncodedDouble:
      irEncodedValue->u.double_value = ParseFloatValue<double>(pptr, arg + 1);
      break;

    case dex::kEncodedString: {
      dex::u4 index = ParseIntValue<dex::u4>(pptr, arg + 1);
      irEncodedValue->u.string_value = GetString(index);
    } break;

    case dex::kEncodedType: {
      dex::u4 index = ParseIntValue<dex::u4>(pptr, arg + 1);
      irEncodedValue->u.type_value = GetType(index);
    } break;

    case dex::kEncodedField: {
      dex::u4 index = ParseIntValue<dex::u4>(pptr, arg + 1);
      irEncodedValue->u.field_value = GetFieldDecl(index);
    } break;

    case dex::kEncodedMethod: {
      dex::u4 index = ParseIntValue<dex::u4>(pptr, arg + 1);
      irEncodedValue->u.method_value = GetMethodDecl(index);
    } break;

    case dex::kEncodedEnum: {
      dex::u4 index = ParseIntValue<dex::u4>(pptr, arg + 1);
      irEncodedValue->u.enum_value = GetFieldDecl(index);
    } break;

    case dex::kEncodedArray:
      CHECK(arg == 0);
      irEncodedValue->u.array_value = ParseEncodedArray(pptr);
      break;

    case dex::kEncodedAnnotation:
      CHECK(arg == 0);
      irEncodedValue->u.annotation_value = ParseAnnotation(pptr);
      break;

    case dex::kEncodedNull:
      CHECK(arg == 0);
      break;

    case dex::kEncodedBoolean:
      CHECK(arg < 2);
      irEncodedValue->u.bool_value = (arg == 1);
      break;

    default:
      CHECK(!"unexpected value type");
  }

  EXTRA(irEncodedValue->original = slicer::MemView(basePtr, *pptr - basePtr));

  return irEncodedValue;
}

ir::Annotation* Reader::ParseAnnotation(const dex::u1** pptr) {
  auto irAnnotation = dex_ir_->Alloc<ir::Annotation>();

  dex::u4 typeIndex = dex::ReadULeb128(pptr);
  dex::u4 elementsCount = dex::ReadULeb128(pptr);

  irAnnotation->type = GetType(typeIndex);
  irAnnotation->visibility = dex::kVisibilityEncoded;

  for (dex::u4 i = 0; i < elementsCount; ++i) {
    auto irElement = dex_ir_->Alloc<ir::AnnotationElement>();

    irElement->name = GetString(dex::ReadULeb128(pptr));
    irElement->value = ParseEncodedValue(pptr);

    irAnnotation->elements.push_back(irElement);
  }

  return irAnnotation;
}

ir::EncodedArray* Reader::ParseEncodedArray(const dex::u1** pptr) {
  auto irEncodedArray = dex_ir_->Alloc<ir::EncodedArray>();

  dex::u4 count = dex::ReadULeb128(pptr);
  for (dex::u4 i = 0; i < count; ++i) {
    irEncodedArray->values.push_back(ParseEncodedValue(pptr));
  }

  return irEncodedArray;
}

ir::EncodedArray* Reader::ExtractEncodedArray(dex::u4 offset) {
  if (offset == 0) {
    return nullptr;
  }

  // first check if we already extracted the same "annotation_item"
  auto& irEncodedArray = encoded_arrays_[offset];
  if (irEncodedArray == nullptr) {
    auto ptr = dataPtr<dex::u1>(offset);
    irEncodedArray = ParseEncodedArray(&ptr);
  }
  return irEncodedArray;
}

ir::DebugInfo* Reader::ExtractDebugInfo(dex::u4 offset) {
  if (offset == 0) {
    return nullptr;
  }

  auto irDebugInfo = dex_ir_->Alloc<ir::DebugInfo>();
  const dex::u1* ptr = dataPtr<dex::u1>(offset);

  irDebugInfo->line_start = dex::ReadULeb128(&ptr);

  // TODO: implicit this param for non-static methods?
  dex::u4 paramCount = dex::ReadULeb128(&ptr);
  for (dex::u4 i = 0; i < paramCount; ++i) {
    dex::u4 nameIndex = dex::ReadULeb128(&ptr) - 1;
    auto ir_string =
        (nameIndex == dex::kNoIndex) ? nullptr : GetString(nameIndex);
    irDebugInfo->param_names.push_back(ir_string);
  }

  // parse the debug info opcodes and note the
  // references to strings and types (to make sure the IR
  // is the full closure of all referenced items)
  //
  // TODO: design a generic debug info iterator?
  //
  auto basePtr = ptr;
  dex::u1 opcode = 0;
  while ((opcode = *ptr++) != dex::DBG_END_SEQUENCE) {
    switch (opcode) {
      case dex::DBG_ADVANCE_PC:
        // addr_diff
        dex::ReadULeb128(&ptr);
        break;

      case dex::DBG_ADVANCE_LINE:
        // line_diff
        dex::ReadSLeb128(&ptr);
        break;

      case dex::DBG_START_LOCAL: {
        // register_num
        dex::ReadULeb128(&ptr);

        dex::u4 nameIndex = dex::ReadULeb128(&ptr) - 1;
        if (nameIndex != dex::kNoIndex) {
          GetString(nameIndex);
        }

        dex::u4 typeIndex = dex::ReadULeb128(&ptr) - 1;
        if (typeIndex != dex::kNoIndex) {
          GetType(typeIndex);
        }
      } break;

      case dex::DBG_START_LOCAL_EXTENDED: {
        // register_num
        dex::ReadULeb128(&ptr);

        dex::u4 nameIndex = dex::ReadULeb128(&ptr) - 1;
        if (nameIndex != dex::kNoIndex) {
          GetString(nameIndex);
        }

        dex::u4 typeIndex = dex::ReadULeb128(&ptr) - 1;
        if (typeIndex != dex::kNoIndex) {
          GetType(typeIndex);
        }

        dex::u4 sigIndex = dex::ReadULeb128(&ptr) - 1;
        if (sigIndex != dex::kNoIndex) {
          GetString(sigIndex);
        }
      } break;

      case dex::DBG_END_LOCAL:
      case dex::DBG_RESTART_LOCAL:
        // register_num
        dex::ReadULeb128(&ptr);
        break;

      case dex::DBG_SET_FILE: {
        dex::u4 nameIndex = dex::ReadULeb128(&ptr) - 1;
        if (nameIndex != dex::kNoIndex) {
          GetString(nameIndex);
        }
      } break;
    }
  }

  irDebugInfo->data = slicer::MemView(basePtr, ptr - basePtr);

  return irDebugInfo;
}

ir::Code* Reader::ExtractCode(dex::u4 offset) {
  if (offset == 0) {
    return nullptr;
  }

  CHECK(offset % 4 == 0);

  auto dexCode = dataPtr<dex::Code>(offset);
  auto irCode = dex_ir_->Alloc<ir::Code>();

  irCode->registers = dexCode->registers_size;
  irCode->ins_count = dexCode->ins_size;
  irCode->outs_count = dexCode->outs_size;

  // instructions array
  irCode->instructions =
      slicer::ArrayView<const dex::u2>(dexCode->insns, dexCode->insns_size);

  // parse the instructions to discover references to other
  // IR nodes (see debug info stream parsing too)
  ParseInstructions(irCode->instructions);

  // try blocks & handlers
  //
  // TODO: a generic try/catch blocks iterator?
  //
  if (dexCode->tries_size != 0) {
    dex::u4 alignedCount = (dexCode->insns_size + 1) / 2 * 2;
    auto tries =
        reinterpret_cast<const dex::TryBlock*>(dexCode->insns + alignedCount);
    auto handlersList =
        reinterpret_cast<const dex::u1*>(tries + dexCode->tries_size);

    irCode->try_blocks =
        slicer::ArrayView<const dex::TryBlock>(tries, dexCode->tries_size);

    // parse the handlers list (and discover embedded references)
    auto ptr = handlersList;

    dex::u4 handlersCount = dex::ReadULeb128(&ptr);
    CHECK(handlersCount <= dexCode->tries_size);

    for (dex::u4 handlerIndex = 0; handlerIndex < handlersCount;
         ++handlerIndex) {
      int catchCount = dex::ReadSLeb128(&ptr);

      for (int catchIndex = 0; catchIndex < std::abs(catchCount);
           ++catchIndex) {
        dex::u4 typeIndex = dex::ReadULeb128(&ptr);
        GetType(typeIndex);

        // address
        dex::ReadULeb128(&ptr);
      }

      if (catchCount < 1) {
        // catch_all_addr
        dex::ReadULeb128(&ptr);
      }
    }

    irCode->catch_handlers = slicer::MemView(handlersList, ptr - handlersList);
  }

  irCode->debug_info = ExtractDebugInfo(dexCode->debug_info_off);

  return irCode;
}

ir::EncodedMethod* Reader::ParseEncodedMethod(const dex::u1** pptr, dex::u4* baseIndex) {
  auto irEncodedMethod = dex_ir_->Alloc<ir::EncodedMethod>();

  auto methodIndex = dex::ReadULeb128(pptr);
  CHECK(methodIndex != dex::kNoIndex);
  if (*baseIndex != dex::kNoIndex) {
    CHECK(methodIndex != 0);
    methodIndex += *baseIndex;
  }
  *baseIndex = methodIndex;

  irEncodedMethod->method = GetMethodDecl(methodIndex);
  irEncodedMethod->access_flags = dex::ReadULeb128(pptr);

  dex::u4 codeOffset = dex::ReadULeb128(pptr);
  irEncodedMethod->code = ExtractCode(codeOffset);

  return irEncodedMethod;
}

ir::Type* Reader::ParseType(dex::u4 index) {
  auto& dexType = TypeIds()[index];
  auto ir_type = dex_ir_->Alloc<ir::Type>();

  ir_type->descriptor = GetString(dexType.descriptor_idx);
  ir_type->orig_index = index;

  return ir_type;
}

ir::FieldDecl* Reader::ParseFieldDecl(dex::u4 index) {
  auto& dexField = FieldIds()[index];
  auto ir_field = dex_ir_->Alloc<ir::FieldDecl>();

  ir_field->name = GetString(dexField.name_idx);
  ir_field->type = GetType(dexField.type_idx);
  ir_field->parent = GetType(dexField.class_idx);
  ir_field->orig_index = index;

  return ir_field;
}

ir::MethodDecl* Reader::ParseMethodDecl(dex::u4 index) {
  auto& dexMethod = MethodIds()[index];
  auto ir_method = dex_ir_->Alloc<ir::MethodDecl>();

  ir_method->name = GetString(dexMethod.name_idx);
  ir_method->prototype = GetProto(dexMethod.proto_idx);
  ir_method->parent = GetType(dexMethod.class_idx);
  ir_method->orig_index = index;

  return ir_method;
}

ir::TypeList* Reader::ExtractTypeList(dex::u4 offset) {
  if (offset == 0) {
    return nullptr;
  }

  // first check to see if we already extracted the same "type_list"
  auto& irTypeList = type_lists_[offset];
  if (irTypeList == nullptr) {
    irTypeList = dex_ir_->Alloc<ir::TypeList>();

    auto dexTypeList = dataPtr<dex::TypeList>(offset);
    CHECK(dexTypeList->size > 0);

    for (dex::u4 i = 0; i < dexTypeList->size; ++i) {
      irTypeList->types.push_back(GetType(dexTypeList->list[i].type_idx));
    }
  }

  return irTypeList;
}

ir::Proto* Reader::ParseProto(dex::u4 index) {
  auto& dexProto = ProtoIds()[index];
  auto ir_proto = dex_ir_->Alloc<ir::Proto>();

  ir_proto->shorty = GetString(dexProto.shorty_idx);
  ir_proto->return_type = GetType(dexProto.return_type_idx);
  ir_proto->param_types = ExtractTypeList(dexProto.parameters_off);
  ir_proto->orig_index = index;

  return ir_proto;
}

ir::String* Reader::ParseString(dex::u4 index) {
  auto ir_string = dex_ir_->Alloc<ir::String>();

  auto data = GetStringData(index);
  auto cstr = data;
  dex::ReadULeb128(&cstr);
  size_t size =
      (cstr - data) + ::strlen(reinterpret_cast<const char*>(cstr)) + 1;

  ir_string->data = slicer::MemView(data, size);
  ir_string->orig_index = index;

  return ir_string;
}

void Reader::ParseInstructions(slicer::ArrayView<const dex::u2> code) {
  const dex::u2* ptr = code.begin();
  while (ptr < code.end()) {
    auto dex_instr = dex::DecodeInstruction(ptr);

    dex::u4 index = dex::kNoIndex;
    switch (dex::GetFormatFromOpcode(dex_instr.opcode)) {
      case dex::kFmt20bc:
      case dex::kFmt21c:
      case dex::kFmt31c:
      case dex::kFmt35c:
      case dex::kFmt3rc:
        index = dex_instr.vB;
        break;

      case dex::kFmt22c:
        index = dex_instr.vC;
        break;

      default:
        break;
    }

    switch (GetIndexTypeFromOpcode(dex_instr.opcode)) {
      case dex::kIndexStringRef:
        GetString(index);
        break;

      case dex::kIndexTypeRef:
        GetType(index);
        break;

      case dex::kIndexFieldRef:
        GetFieldDecl(index);
        break;

      case dex::kIndexMethodRef:
        GetMethodDecl(index);
        break;

      default:
        break;
    }

    auto isize = dex::GetWidthFromBytecode(ptr);
    CHECK(isize > 0);
    ptr += isize;
  }
  CHECK(ptr == code.end());
}

// Basic .dex header structural checks
void Reader::ValidateHeader() {
  CHECK(size_ > sizeof(dex::Header));

  CHECK(header_->file_size == size_);
  CHECK(header_->header_size == sizeof(dex::Header));
  CHECK(header_->endian_tag == dex::kEndianConstant);
  CHECK(header_->data_size % 4 == 0);
  CHECK(header_->data_off + header_->data_size <= size_);
  CHECK(header_->string_ids_off % 4 == 0);
  CHECK(header_->type_ids_size < 65536);
  CHECK(header_->type_ids_off % 4 == 0);
  CHECK(header_->proto_ids_size < 65536);
  CHECK(header_->proto_ids_off % 4 == 0);
  CHECK(header_->field_ids_off % 4 == 0);
  CHECK(header_->method_ids_off % 4 == 0);
  CHECK(header_->class_defs_off % 4 == 0);
  CHECK(header_->map_off >= header_->data_off && header_->map_off < size_);
  CHECK(header_->link_size == 0);
  CHECK(header_->link_off == 0);
  CHECK(header_->data_off % 4 == 0);
  CHECK(header_->map_off % 4 == 0);
  CHECK(header_->data_off + header_->data_size == size_);

  // validate the map
  // (map section size = sizeof(MapList::size) + sizeof(MapList::list[size])
  auto map_list = ptr<dex::MapList>(header_->map_off);
  CHECK(map_list->size > 0);
  auto map_section_size =
      sizeof(dex::u4) + sizeof(dex::MapItem) * map_list->size;
  CHECK(header_->map_off + map_section_size <= size_);
}

}  // namespace dex
