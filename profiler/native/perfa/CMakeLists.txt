add_library(perfa SHARED
            perfa.cc
            jvmti_helper.cc)

# Include agents's symbol into agent's dynamic symbol table as agent relies
# on dlsym to bind the native methods
SET(PERFA_LIB -Wl,--whole-archive agent -Wl,--no-whole-archive)

target_link_libraries(perfa
                      ${PERFA_LIB}
                      slicer
                      profiler_utils
                      profiler_protos)

set_linker_script(perfa perfa.exports perfa.cc)

install_library(perfa)

