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

# Sets the linker script for the given target
#   target:          Name of the target
#   linker_script:   Path of the linker script
#   dependency_file: Any source file what is part of the given target. It is
#                    used to set correct dependencies for the linker script.
function(set_linker_script target linker_script dependency_file)
  if (ANDROID)
    get_filename_component(linker_script_path ${linker_script} ABSOLUTE)

    set_target_properties(${target} PROPERTIES
      LINK_FLAGS "-Wl,--version-script,${linker_script_path}")

    # HACK: To ensure that we re-link the executable after a change in the linker
    # script we set a source level dependency for one of the source file used in
    # the targt.
    set_source_files_properties(${dependency_file} PROPERTIES
      OBJECT_DEPENDS "${linker_script_path}")
  endif()
endfunction()
