add_library(perfa SHARED
            support/vm_stats_sampler.cc
            support/profiler_service.cc
            support/event_passthrough.cc
            support/http_tracker.cc
            perfa.cc)

target_link_libraries(perfa
                      profiler_protos
                      profiler_utils)

set_linker_script(perfa perfa.exports perfa.cc)

install_library(perfa)
