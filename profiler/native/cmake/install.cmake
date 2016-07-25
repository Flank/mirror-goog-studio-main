# Copyright (C) 2016 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

function(_install_cross_target target stripped_file)
  add_custom_command(OUTPUT ${stripped_file}
                     COMMAND "${CMAKE_COMMAND}" "-E" "copy" "$<TARGET_FILE:${target}>" "${stripped_file}"
                     COMMAND "${CMAKE_STRIP}" "${stripped_file}"
                     DEPENDS ${target})
  add_custom_target("strip-${target}" ALL DEPENDS ${stripped_file})

  install(PROGRAMS ${stripped_file}
          DESTINATION .)
endfunction()

function(install_library target)
  if (CMAKE_CROSSCOMPILING)
    _install_cross_target(${target} "${CMAKE_CURRENT_BINARY_DIR}/stripped/lib${target}.so")
  else()
    install(TARGETS ${target}
            LIBRARY DESTINATION .)
  endif()
endfunction()

function(install_executable target)
  if (CMAKE_CROSSCOMPILING)
    _install_cross_target(${target} "${CMAKE_CURRENT_BINARY_DIR}/stripped/${target}")
  else()
    install(TARGETS ${target}
            RUNTIME DESTINATION .)
  endif()
endfunction()
