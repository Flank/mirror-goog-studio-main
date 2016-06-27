add_library(profiler_network
            connection_sampler.cc
            connectivity_sampler.cc
            internal_network_service.cc
            network_cache.cc
            network_collector.cc
            network_constants.cc
            network_profiler_service.cc
            network_sampler.cc
            traffic_sampler.cc)

target_link_libraries(profiler_network
                      profiler_protos
                      profiler_utils)

add_unit_test(profiler_network_test
              connection_sampler_test.cc
              connectivity_sampler_test.cc
              network_sampler_test.cc
              traffic_sampler_test.cc)

target_link_libraries(profiler_network_test
                      profiler_network
                      ${GTEST_LINK_LIBRARIES})
