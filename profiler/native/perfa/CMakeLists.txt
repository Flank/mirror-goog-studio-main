set(PERFA_SOURCE
    support/event_passthrough.cc
    support/event_manager.cc
    support/http_tracker.cc
    support/vm_stats_sampler.cc
    perfa.cc)

add_library(perfa SHARED
            ${PERFA_SOURCE})

add_library(perfa_static STATIC
            ${PERFA_SOURCE})

target_link_libraries(perfa
                      profiler_protos
                      profiler_utils)

target_link_libraries(perfa_static
                      profiler_protos
                      profiler_utils)

set_linker_script(perfa perfa.exports perfa.cc)

install_library(perfa)
