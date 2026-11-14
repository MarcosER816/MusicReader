cmake_minimum_required(VERSION 3.22.1)
project(musicreader_audio LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(oboe REQUIRED CONFIG)

# Pure DSP, also built on the host for the GoogleTest tests (audio/src/test/cpp).
add_subdirectory(dsp)

add_library(musicreader_audio SHARED
        jni_bridge.cpp
        engine/AudioEngine.cpp)
target_include_directories(musicreader_audio PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
# The engine's callback and the clock math run on every audio block: optimize in debug too.
target_compile_options(musicreader_audio PRIVATE -O2)

target_link_libraries(musicreader_audio
        musicreader_dsp
        oboe::oboe)
