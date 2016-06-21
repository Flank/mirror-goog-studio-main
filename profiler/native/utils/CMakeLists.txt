set(UTILS_SOURCES
    activity_manager.cc
    bash_command.cc
    file_reader.cc
    installer.cc
    package_manager.cc
    profiler_file.cc
    stopwatch.cc
    token.cc)

if (CMAKE_HOST_APPLE AND NOT ANDROID)
    set(UTILS_SOURCES ${UTILS_SOURCES} clock.mac.cc)
else()
    set(UTILS_SOURCES ${UTILS_SOURCES} clock.cc)
endif()

if (ANDROID)
    set(UTILS_SOURCES ${UTILS_SOURCES} log.android.cc)
else()
    set(UTILS_SOURCES ${UTILS_SOURCES} log.cc)
endif()

add_library(profiler_utils
            ${UTILS_SOURCES})

add_unit_test(profiler_utils_test
              file_reader_test.cc
              clock_test.cc
              stopwatch_test.cc
              time_value_buffer_test.cc
              timespec_math_test.cc)

target_link_libraries(profiler_utils_test
                      profiler_utils
                      ${GTEST_LINK_LIBRARIES})
