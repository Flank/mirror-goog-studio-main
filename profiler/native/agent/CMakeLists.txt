set(AGENT_SOURCE
    support/event_passthrough.cc
    support/event_manager.cc
    support/http_tracker.cc
    support/memory_stats_logger.cc
    agent.cc)

add_library(supportjni SHARED
            ${AGENT_SOURCE})

add_library(agent STATIC
            ${AGENT_SOURCE})

target_link_libraries(supportjni
                      profiler_protos
                      profiler_utils)

target_link_libraries(agent
                      profiler_protos
                      profiler_utils)

set_linker_script(supportjni agent.exports agent.cc)

install_library(supportjni)
