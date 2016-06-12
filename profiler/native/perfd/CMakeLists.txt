add_executable(perfd
               daemon.cc
               perfa_service.cc
               perfd.cc
               profiler_service.cc)

target_link_libraries(perfd
                      profiler_cpu
                      profiler_memory
                      profiler_network
                      profiler_protos
                      profiler_utils)

# Add perfd profiler components

add_subdirectory(cpu)
add_subdirectory(memory)
add_subdirectory(network)
