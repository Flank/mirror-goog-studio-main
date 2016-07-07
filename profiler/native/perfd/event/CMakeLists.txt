add_library(profiler_event
            event_cache.cc
            event_service.cc
            internal_event_service.cc)

target_link_libraries(profiler_event
                      profiler_protos
                      profiler_utils)