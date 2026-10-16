cmake_minimum_required(VERSION 3.22.1)

project("openscanner")

add_library(
    openscanner
    SHARED
    native_scanner.cpp
    edge_detector.cpp
    perspective_warp.cpp
    magic_color.cpp
)

find_library(
    log-lib
    log
)

find_library(
    jnigraphics-lib
    jnigraphics
)

target_link_libraries(
    openscanner
    ${log-lib}
    ${jnigraphics-lib}
)
