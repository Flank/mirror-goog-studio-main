add_executable(perfd
               daemon.cc
               perfa_service.cc
               perfd.cc
               profiler_service.cc)

target_link_libraries(perfd
                      profiler_cpu
                      profiler_event
                      profiler_memory
                      profiler_network
                      profiler_energy
                      profiler_protos
                      profiler_utils)

# Add perfd profiler components
add_subdirectory(cpu)
add_subdirectory(energy)
add_subdirectory(event)
add_subdirectory(memory)
add_subdirectory(network)

install(PROGRAMS $<TARGET_FILE:perfd>
        DESTINATION .)
