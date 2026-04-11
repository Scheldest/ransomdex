cmake_minimum_required(VERSION 3.22.1)

project("ransom-native")

add_library(
    ransom-native
    SHARED
    native-lib.cpp
)

target_link_libraries(ransom-native android log)