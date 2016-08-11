add_library(profiler_energy
            energy_cache.cc
            energy_collector.cc
            energy_service.cc
            energy_usage_sampler.cc
            internal_energy_service.cc)

target_link_libraries(profiler_energy
                      profiler_protos
                      profiler_utils)

add_unit_test(profiler_energy_test
              energy_usage_sampler_test.cc
              energy_cache_test.cc)

target_link_libraries(profiler_energy_test
                      profiler_energy
                      ${GTEST_LINK_LIBRARIES})
