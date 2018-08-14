/*
 * Copyright (C) 2018 The Android Open Source Project
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
 *
 */

#include <stdio.h>

#include <stdlib.h>
#include <stdint.h>
#include <inttypes.h>

size_t kLineSize = 12;

// Use a simple hashing function from Java string specifications.
uint64_t GenerateHash(const uint8_t* buffer, size_t size) {
  uint64_t hash = 0;
  for (size_t i = 0 ; i < size ; i++) {
    hash = (hash << 5) - hash + buffer[i];
  }
  return hash;
}

int main(int argc, char** argv) {
   if (argc != 4) {
     fprintf(stderr, "Usage %s binary_path cc_path variable_name", argv[0]);
     return EXIT_FAILURE;
   }

   const char* src_file = argv[1];
   const char* dst_dile = argv[2];
   const char* var_name = argv[3];

   FILE* input_file = fopen(src_file, "rb");
   if (input_file == 0) {
     fprintf(stderr, "Unable to open input file '%s'.", src_file);
     return EXIT_FAILURE;
   }

   fseek(input_file, 0L, SEEK_END);
   size_t size = ftell(input_file);
   fseek(input_file, 0L, SEEK_SET);

   // Load file to ram.
   uint8_t* buffer = (uint8_t*)malloc(size);
   size_t read = fread(buffer, 1, size, input_file);
   if (read == 0) {
     fprintf(stderr, "Unable to read file '%s'.", src_file);
     return EXIT_FAILURE;
   }


   FILE* output_file = fopen(dst_dile, "wb");

   // Generate array.
   fprintf(output_file, "unsigned char %s[] = {", var_name);
   for (size_t i = 0 ; i < size ; i++) {
     if (i % kLineSize == 0) {
       fprintf(output_file, "\n");
     }
     fprintf(output_file, "0x%02x, ", buffer[i]);
   }
   fprintf(output_file, "};\n");

   // Generate len and hash.
   fprintf(output_file, "uint64_t %s_len = 0x%zx;\n", var_name, size);
   uint64_t hash = GenerateHash(buffer, size);
   fprintf(output_file, "uint64_t %s_hash = %" PRIu64 "u;\n", var_name, hash);

   // Clean up.
   fclose(output_file);
   fclose(input_file);
   free(buffer);

 }