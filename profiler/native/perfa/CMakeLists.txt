add_library(perfa SHARED
            support/profiler_service.cc
            support/event_passthrough.cc
            support/http_tracker.cc
            perfa.cc)

target_link_libraries(perfa
                      profiler_protos
                      profiler_utils)

if(CMAKE_RUNTIME_OUTPUT_DIRECTORY)
  set_target_properties(perfa PROPERTIES
                        LIBRARY_OUTPUT_DIRECTORY ${CMAKE_RUNTIME_OUTPUT_DIRECTORY})
endif()
