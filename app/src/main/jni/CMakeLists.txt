# Sets the minimum version of CMake required to build your native library.
cmake_minimum_required(VERSION 3.18.1)

project("cafemode_native")

# This is the new, more forceful way to add the include path.
# It adds the NDK's system header folder to the search path for all targets.
include_directories(${ANDROID_NDK}/toolchains/llvm/prebuilt/darwin-x86_64/sysroot/usr/include)

# Adds your C++ source file to the library.
add_library(
        cafemode_native
        SHARED
        native-lib.cpp)

# Links the required compiled libraries.
target_link_libraries(
        cafemode_native
        PRIVATE
        audioeffect
        log)