add_library(profiler_memory
            memory_cache.cc
            memory_collector.cc
            memory_service.cc
            memory_levels_sampler.cc)

target_link_libraries(profiler_memory
                      profiler_protos)

add_unit_test(perfd_memory_tests
              memory_levels_sampler_test.cc)

target_link_libraries(perfd_memory_tests
                      profiler_memory
                      profiler_utils
                      ${GTEST_LINK_LIBRARIES})
