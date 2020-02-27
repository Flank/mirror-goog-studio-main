cmake_minimum_required(VERSION 3.6)
project(app VERSION 1.0.0 LANGUAGES CXX)

find_package(curl REQUIRED CONFIG)
find_package(jsoncpp REQUIRED CONFIG)
find_library(LIBLOG log)

add_library(app SHARED app.cpp)
set_target_properties(app PROPERTIES CXX_STANDARD 17)
target_compile_options(app PRIVATE -Wall -Werror)
target_link_libraries(app ${LIBLOG} curl::curl jsoncpp::jsoncpp)
