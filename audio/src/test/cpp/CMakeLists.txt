# Host build of the audio DSP with GoogleTest (BSD-3-Clause). Run:
#   cmake -S audio/src/test/cpp -B build/native-tests && cmake --build build/native-tests && ctest --test-dir build/native-tests
cmake_minimum_required(VERSION 3.22)
project(musicreader_dsp_tests LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

find_package(Threads REQUIRED)
find_package(GTest REQUIRED)

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../main/cpp/dsp dsp)

add_executable(dsp_tests
        RingBufferTest.cpp
        ClockTest.cpp
        BeatGridTest.cpp
        RendererTest.cpp
        CorrelationTest.cpp
        CalibrationTest.cpp)
target_compile_options(dsp_tests PRIVATE -Wall -Wextra -Werror)
target_link_libraries(dsp_tests PRIVATE musicreader_dsp GTest::gtest GTest::gtest_main Threads::Threads)

include(GoogleTest)
enable_testing()
gtest_discover_tests(dsp_tests)
