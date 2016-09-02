set(UTILS_SOURCES
    activity_manager.cc
    app_base.cc
    bash_command.cc
    file_reader.cc
    installer.cc
    package_manager.cc
    process_manager.cc
    profiler_file.cc
    stopwatch.cc
    tokenizer.cc
    trace.cc
    fs/dir.cc
    fs/disk_file_system.cc
    fs/file.cc
    fs/file_system.cc
    fs/path.cc)

if (CMAKE_HOST_APPLE AND NOT ANDROID)
    set(UTILS_SOURCES ${UTILS_SOURCES} clock.mac.cc)
    set(UTILS_SOURCES ${UTILS_SOURCES} thread_name.mac.cc)
    set(UTILS_SOURCES ${UTILS_SOURCES} filesystem_notifier.mac.cc)
else()
    set(UTILS_SOURCES ${UTILS_SOURCES} clock.cc)
    set(UTILS_SOURCES ${UTILS_SOURCES} thread_name.cc)
    set(UTILS_SOURCES ${UTILS_SOURCES} filesystem_notifier.cc)
endif()

if (ANDROID)
    set(UTILS_SOURCES ${UTILS_SOURCES} log.android.cc)
else()
    set(UTILS_SOURCES ${UTILS_SOURCES} log.cc)
endif()

add_library(profiler_utils
            ${UTILS_SOURCES})

if (TRACE_OUTPUT)
    target_compile_definitions(profiler_utils PRIVATE TRACE_OUTPUT=1)
endif()

add_unit_test(profiler_utils_test
              file_reader_test.cc
              clock_test.cc
              stopwatch_test.cc
              time_value_buffer_test.cc
              timespec_math_test.cc
              tokenizer_test.cc
              uid_fetcher_test.cc
              fs/memory_file_system.cc
              fs/file_system_test.cc)

target_link_libraries(profiler_utils_test
                      profiler_utils
                      ${GTEST_LINK_LIBRARIES})
