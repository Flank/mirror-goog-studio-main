cmake_minimum_required(VERSION 3.6)
project(foo VERSION 1.0.0 LANGUAGES CXX)

add_library(foo_static STATIC foo.cpp)
target_include_directories(foo_static PUBLIC include)

add_library(foo SHARED foo.cpp)
target_include_directories(foo PUBLIC include)
