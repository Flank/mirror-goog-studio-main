add_library(profiler_network
            connection_sampler.cc
            connectivity_sampler.cc
            internal_network_service.cc
            net_stats_file_reader.cc
            network_cache.cc
            network_collector.cc
            network_service.cc
            io_network_type_provider.cc
            speed_converter.cc
            speed_sampler.cc)

target_link_libraries(profiler_network
                      profiler_protos
                      profiler_utils)

add_unit_test(profiler_network_test
              connection_sampler_test.cc
              connectivity_sampler_test.cc
              net_stats_file_reader_test.cc
              speed_converter_test.cc)

target_link_libraries(profiler_network_test
                      profiler_network
                      testutils
                      ${GTEST_LINK_LIBRARIES})
