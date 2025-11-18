cmake_minimum_required(VERSION 3.13)
project(piomatterjni C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

find_package(JNI REQUIRED)
include_directories(${JNI_INCLUDE_DIRS})
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/jni)

# Headers del projecte Adafruit
include_directories(${CMAKE_SOURCE_DIR}/../src/include)
include_directories(${CMAKE_SOURCE_DIR}/../src/piolib/include)

add_library(piomatterjni SHARED
    jni/piomatter_backend.cpp
    jni/piomatter_jni.c
    ${CMAKE_SOURCE_DIR}/../src/piolib/piolib.c
    ${CMAKE_SOURCE_DIR}/../src/piolib/pio_rp1.c
)

target_link_libraries(piomatterjni PRIVATE ${JNI_LIBRARIES} pthread)

set_target_properties(piomatterjni PROPERTIES
    OUTPUT_NAME "piomatterjni"
    PREFIX "lib"
)
