# Pure C++17 audio DSP: no Android, no Oboe. Built into the app and on the host for tests.
add_library(musicreader_dsp STATIC
        Sounds.cpp
        Renderer.cpp
        Correlation.cpp
        Onsets.cpp
        Calibration.cpp)
target_include_directories(musicreader_dsp PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})
target_compile_features(musicreader_dsp PUBLIC cxx_std_17)
# Calibration analysis runs millions of multiply-adds: optimize even in debug builds.
target_compile_options(musicreader_dsp PRIVATE -O2 -Wall -Wextra -Werror)
set_target_properties(musicreader_dsp PROPERTIES POSITION_INDEPENDENT_CODE ON)
