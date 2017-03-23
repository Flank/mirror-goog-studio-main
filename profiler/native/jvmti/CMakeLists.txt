add_library(agent SHARED
            agent.cc
            jvmti_helper.cc)

# Include perfa's symbol into agent's dynamic symbol table as agent relies
# on dlsym to bind the native methods
SET(PERFA_LIB -Wl,--whole-archive perfa_static -Wl,--no-whole-archive)

target_link_libraries(agent
                      ${PERFA_LIB}
                      profiler_utils
                      profiler_protos)

set_linker_script(agent agent.exports agent.cc)

install_library(agent)

