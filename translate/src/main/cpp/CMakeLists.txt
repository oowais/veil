cmake_minimum_required(VERSION 3.31.6)

project("veil_llama" LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED true)

if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_SYSTEM_ARCH "ARM")
    set(GGML_CPU_KLEIDIAI ON)
elseif(ANDROID_ABI STREQUAL "x86_64")
    # Emulator builds only (-PveilAbi=x86_64, for README screenshots): one plain x86 CPU backend.
    set(GGML_CPU_ALL_VARIANTS OFF CACHE BOOL "" FORCE)
else()
    message(FATAL_ERROR "Veil targets arm64-v8a, and x86_64 for the emulator (got ${ANDROID_ABI})")
endif()
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
