add_library(agent SHARED
            agent.cc)

target_link_libraries(agent
                      profiler_utils)

set_linker_script(agent agent.exports agent.cc)

install_library(agent)
