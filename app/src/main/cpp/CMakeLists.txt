cmake_minimum_required(VERSION 3.22.1)
project(wjx_xiaozhi_opus LANGUAGES CXX)

find_package(opus REQUIRED CONFIG)

add_library(
    wjx_xiaozhi_opus
    SHARED
    xiaozhi_opus_jni.cpp
)

target_compile_features(wjx_xiaozhi_opus PRIVATE cxx_std_17)
target_compile_options(wjx_xiaozhi_opus PRIVATE -Wall -Wextra -Werror)

target_link_libraries(
    wjx_xiaozhi_opus
    opus::libopus.a
    log
)
