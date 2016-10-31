add_library(profiler_cpu
            cpu_cache.cc
            cpu_collector.cc
            cpu_service.cc
            cpu_usage_sampler.cc
            procfs_files.cc
            simpleperf_manager.cc
            thread_monitor.cc)

target_link_libraries(profiler_cpu
                      profiler_protos
                      profiler_utils)

add_unit_test(profiler_cpu_test
              cpu_usage_sampler_test.cc)

target_link_libraries(profiler_cpu_test
                      profiler_cpu
                      testutils
                      ${GTEST_LINK_LIBRARIES})
