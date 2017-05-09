add_library(profiler_graphics
            graphics_collector.cc
            graphics_service.cc)

target_link_libraries(profiler_graphics
                      profiler_utils
                      profiler_protos)
