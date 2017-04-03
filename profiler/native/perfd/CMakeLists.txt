add_executable(perfd
               daemon.cc
               agent_service.cc
               perfd.cc
               profiler_service.cc
               generic_component.cc)

target_link_libraries(perfd
                      profiler_cpu
                      profiler_event
                      profiler_memory
                      profiler_network
                      profiler_protos
                      profiler_utils)

set_linker_script(perfd perfd.exports perfd.cc)

# Add perfd profiler components
add_subdirectory(cpu)
add_subdirectory(event)
add_subdirectory(memory)
add_subdirectory(network)

install_executable(perfd)
