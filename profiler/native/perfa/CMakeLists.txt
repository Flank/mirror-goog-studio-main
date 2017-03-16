add_library(perfa SHARED
            support/event_passthrough.cc
            support/event_manager.cc
            support/http_tracker.cc
            support/vm_stats_sampler.cc
            perfa.cc)

target_link_libraries(perfa
                      profiler_protos
                      profiler_utils)

set_linker_script(perfa perfa.exports perfa.cc)

install_library(perfa)
