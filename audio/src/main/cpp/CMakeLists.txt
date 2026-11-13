cmake_minimum_required(VERSION 3.22.1)
project(musicreader_audio LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(oboe REQUIRED CONFIG)

add_library(musicreader_audio SHARED
        jni_bridge.cpp)

target_link_libraries(musicreader_audio
        oboe::oboe
        log)
