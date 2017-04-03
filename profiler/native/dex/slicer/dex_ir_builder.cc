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

#include "dex_ir_builder.h"

#include <sstream>
#include <string.h>

namespace ir {

String* Builder::GetAsciiString(const char* str) {
  // look for the string first...
  auto ir_string = dex_ir_->strings_lookup.Lookup(str);
  if(ir_string != nullptr) {
    return ir_string;
  }

  // create a new string data
  dex::u4 len = strlen(str);
  slicer::Buffer buff;
  buff.PushULeb128(len);
  buff.Push(str, len + 1);
  buff.Seal(1);

  // create the new .dex IR string node
  ir_string = dex_ir_->Alloc<String>();
  ir_string->data = slicer::MemView(buff.data(), buff.size());

  // update the index -> ir node map
  auto new_index = dex_ir_->strings_indexes.AllocateIndex();
  auto& ir_node = dex_ir_->strings_map[new_index];
  CHECK(ir_node == nullptr);
  ir_node = ir_string;
  ir_string->orig_index = new_index;

  // attach the new string data to the .dex IR
  dex_ir_->AttachBuffer(std::move(buff));

  // update the strings lookup table
  dex_ir_->strings_lookup.Insert(ir_string);

  return ir_string;
}

Type* Builder::GetType(String* descriptor) {
  // look for an existing type
  for (const auto& ir_type : dex_ir_->types) {
    if (ir_type->descriptor == descriptor) {
      return ir_type.get();
    }
  }

  // create a new type
  auto ir_type = dex_ir_->Alloc<Type>();
  ir_type->descriptor = descriptor;

  // update the index -> ir node map
  auto new_index = dex_ir_->types_indexes.AllocateIndex();
  auto& ir_node = dex_ir_->types_map[new_index];
  CHECK(ir_node == nullptr);
  ir_node = ir_type;
  ir_type->orig_index = new_index;

  return ir_type;
}

TypeList* Builder::GetTypeList(const std::vector<Type*>& types) {
  if (types.empty()) {
    return nullptr;
  }

  // look for an existing TypeList
  for (const auto& ir_type_list : dex_ir_->type_lists) {
    if (ir_type_list->types == types) {
      return ir_type_list.get();
    }
  }

  // create a new TypeList
  auto ir_type_list = dex_ir_->Alloc<TypeList>();
  ir_type_list->types = types;
  return ir_type_list;
}

// Helper for GetProto()
static std::string CreateShorty(Type* return_type, TypeList* param_types) {
  std::stringstream ss;
  ss << dex::DescriptorToShorty(return_type->descriptor->c_str());
  if (param_types != nullptr) {
    for (auto param_type : param_types->types) {
      ss << dex::DescriptorToShorty(param_type->descriptor->c_str());
    }
  }
  return ss.str();
}

Proto* Builder::GetProto(Type* return_type, TypeList* param_types) {
  // create "shorty" descriptor automatically
  auto shorty = GetAsciiString(CreateShorty(return_type, param_types).c_str());

  // look for an existing proto
  for (const auto& ir_proto : dex_ir_->protos) {
    if (ir_proto->shorty == shorty &&
        ir_proto->return_type == return_type &&
        ir_proto->param_types == param_types) {
      return ir_proto.get();
    }
  }

  // create a new proto
  auto ir_proto = dex_ir_->Alloc<Proto>();
  ir_proto->shorty = shorty;
  ir_proto->return_type = return_type;
  ir_proto->param_types = param_types;

  // update the index -> ir node map
  auto new_index = dex_ir_->protos_indexes.AllocateIndex();
  auto& ir_node = dex_ir_->protos_map[new_index];
  CHECK(ir_node == nullptr);
  ir_node = ir_proto;
  ir_proto->orig_index = new_index;

  return ir_proto;
}

FieldDecl* Builder::GetFieldDecl(String* name, Type* type, Type* parent) {
  // look for an existing field
  for (const auto& ir_field : dex_ir_->fields) {
    if (ir_field->name == name &&
        ir_field->type == type &&
        ir_field->parent == parent) {
      return ir_field.get();
    }
  }

  // create a new field declaration
  auto ir_field = dex_ir_->Alloc<FieldDecl>();
  ir_field->name = name;
  ir_field->type = type;
  ir_field->parent = parent;

  // update the index -> ir node map
  auto new_index = dex_ir_->fields_indexes.AllocateIndex();
  auto& ir_node = dex_ir_->fields_map[new_index];
  CHECK(ir_node == nullptr);
  ir_node = ir_field;
  ir_field->orig_index = new_index;

  return ir_field;
}

MethodDecl* Builder::GetMethodDecl(String* name, Proto* proto, Type* parent) {
  // look for an existing method
  for (const auto& ir_method : dex_ir_->methods) {
    if (ir_method->name == name &&
        ir_method->prototype == proto &&
        ir_method->parent == parent) {
      return ir_method.get();
    }
  }

  // create a new method declaration
  auto ir_method = dex_ir_->Alloc<MethodDecl>();
  ir_method->name = name;
  ir_method->prototype = proto;
  ir_method->parent = parent;

  // update the index -> ir node map
  auto new_index = dex_ir_->methods_indexes.AllocateIndex();
  auto& ir_node = dex_ir_->methods_map[new_index];
  CHECK(ir_node == nullptr);
  ir_node = ir_method;
  ir_method->orig_index = new_index;

  return ir_method;
}

} // namespace ir

