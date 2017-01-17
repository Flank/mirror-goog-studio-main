add_executable(dexter
               dexter.cc
               main.cc)

include_directories ("${PROJECT_SOURCE_DIR}/dex")

target_link_libraries(dexter slicer)

install_executable(dexter)
