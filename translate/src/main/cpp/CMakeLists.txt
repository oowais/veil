cmake_minimum_required(VERSION 3.31.6)

project("veil_llama" LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED true)

if(NOT ANDROID_ABI STREQUAL "arm64-v8a")
    message(FATAL_ERROR "Veil only targets arm64-v8a (got ${ANDROID_ABI})")
endif()

set(GGML_SYSTEM_ARCH "ARM")
set(GGML_CPU_KLEIDIAI ON)
set(GGML_OPENMP ON)

set(LLAMA_SRC ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp)
add_subdirectory(${LLAMA_SRC} build-llama)

add_library(veil_llama SHARED veil_llama.cpp)

target_include_directories(veil_llama PRIVATE
        ${LLAMA_SRC}/include
        ${LLAMA_SRC}/ggml/include)

target_link_libraries(veil_llama
        llama
        ggml
        android
        log)
