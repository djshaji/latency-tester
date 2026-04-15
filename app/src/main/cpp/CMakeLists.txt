cmake_minimum_required(VERSION 3.10)
project(audio_checker)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Minimal native library to expose JNI probes. By default this file does not require Oboe so
# the Gradle build can run without adding Oboe as a submodule. To integrate Oboe, add it as a
# subdirectory and link against the `oboe` target (see commented example below).


# If Oboe is present as a submodule under src/main/cpp/oboe, add it here and link.
set(OBOE_DIR ${CMAKE_SOURCE_DIR}/oboe)
if(EXISTS ${OBOE_DIR}/CMakeLists.txt)
    message(STATUS "Including Oboe from ${OBOE_DIR}")
    add_subdirectory(${OBOE_DIR} ${CMAKE_BINARY_DIR}/oboe_build)
    set(OBoeAvailable TRUE)
else()
    message(STATUS "Oboe subdirectory not found; building with native stubs only.")
    set(OBoeAvailable FALSE)
endif()

add_library(audio_checker SHARED
    AudioChecker.cpp
)

if(OBoeAvailable)
    target_link_libraries(audio_checker PRIVATE oboe)
endif()

find_library(log-lib log)
target_link_libraries(audio_checker PRIVATE ${log-lib})


