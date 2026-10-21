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

# Bergamot (Firefox Translations, D-062): Marian with int8 gemm (Ruy on ARM, intgemm on x86),
# linked statically into libveil_bergamot.so. Settings as in the ScreenTranslation Android build.
set(BERGAMOT_SRC ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/bergamot-translator)
set(BUILD_SHARED_LIBS OFF)
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(BUILD_ARCH "armv8-a" CACHE STRING "" FORCE)
else()
    set(BUILD_ARCH "x86-64" CACHE STRING "" FORCE)
    # No BLAS on Android x86: use Ruy for float gemm, as on ARM (Marian aborts without one).
    set(USE_RUY ON CACHE BOOL "" FORCE)
    set(USE_RUY_SGEMM ON CACHE BOOL "" FORCE)
endif()
set(COMPILE_CPU ON CACHE BOOL "" FORCE)
set(COMPILE_CUDA OFF CACHE BOOL "" FORCE)
set(COMPILE_EXAMPLES OFF CACHE BOOL "" FORCE)
set(COMPILE_PYTHON OFF CACHE BOOL "" FORCE)
set(COMPILE_SERVER OFF CACHE BOOL "" FORCE)
set(COMPILE_TESTS OFF CACHE BOOL "" FORCE)
set(COMPILE_UNIT_TESTS OFF CACHE BOOL "" FORCE)
set(ENABLE_CACHE_STATS OFF CACHE BOOL "" FORCE)
set(GIT_SUBMODULE OFF CACHE BOOL "" FORCE)
set(SSPLIT_COMPILE_LIBRARY_ONLY ON CACHE BOOL "" FORCE)
# ssplit's own PCRE2 build is an ExternalProject that runs CMake without a generator, which picks
# Visual Studio on Windows hosts. Fetch the same PCRE2 release (pinned by hash) and build it as a
# normal subdirectory instead.
include(FetchContent)
FetchContent_Declare(pcre2
        URL https://github.com/PhilipHazel/pcre2/releases/download/pcre2-10.39/pcre2-10.39.tar.gz
        URL_HASH SHA256=0781bd2536ef5279b1943471fdcdbd9961a2845e1d2c9ad849b9bd98ba1a9bd4
        DOWNLOAD_EXTRACT_TIMESTAMP TRUE
        SOURCE_SUBDIR do-not-add)
FetchContent_MakeAvailable(pcre2)
set(PCRE2_BUILD_PCRE2GREP OFF CACHE BOOL "" FORCE)
set(PCRE2_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(PCRE2_SUPPORT_JIT ON CACHE BOOL "" FORCE)
set(BUILD_STATIC_LIBS ON CACHE BOOL "" FORCE)
add_subdirectory(${pcre2_SOURCE_DIR} build-pcre2 EXCLUDE_FROM_ALL)
set(SSPLIT_USE_INTERNAL_PCRE2 OFF CACHE BOOL "" FORCE)
set(PCRE2_LIBRARIES pcre2-8-static CACHE STRING "" FORCE)
set(PCRE2_INCLUDE_DIRS ${CMAKE_CURRENT_BINARY_DIR}/build-pcre2 CACHE PATH "" FORCE)
set(USE_MKL OFF CACHE BOOL "" FORCE)
set(USE_STATIC_LIBS ON CACHE BOOL "" FORCE)
add_subdirectory(${BERGAMOT_SRC} build-bergamot EXCLUDE_FROM_ALL)

# SentencePiece (inside Marian) casts -1 to an enum in a constexpr, which clang 19+ rejects. Compile
# a copy of that one file with const instead; the submodule stays untouched.
set(SPM_SRC ${BERGAMOT_SRC}/3rd_party/marian-dev/src/3rd_party/sentencepiece/src)
file(READ ${SPM_SRC}/trainer_interface.cc SPM_TRAINER)
string(REPLACE "constexpr unicode_script::ScriptType kAnyType" "const unicode_script::ScriptType kAnyType"
        SPM_TRAINER "${SPM_TRAINER}")
file(WRITE ${CMAKE_CURRENT_BINARY_DIR}/spm-patched/trainer_interface.cc "${SPM_TRAINER}")
get_target_property(SPM_TRAIN_SOURCES sentencepiece_train-static SOURCES)
list(TRANSFORM SPM_TRAIN_SOURCES REPLACE ".*trainer_interface\\.cc$" ${CMAKE_CURRENT_BINARY_DIR}/spm-patched/trainer_interface.cc)
set_property(TARGET sentencepiece_train-static PROPERTY SOURCES ${SPM_TRAIN_SOURCES})
target_include_directories(sentencepiece_train-static PRIVATE ${SPM_SRC})
if(ANDROID_ABI STREQUAL "x86_64")
    # Emulator builds: Marian's faiss uses SSE intrinsics without including their header.
    target_compile_options(faiss PRIVATE -include immintrin.h)
endif()

add_library(veil_bergamot SHARED veil_bergamot.cpp)
target_include_directories(veil_bergamot PRIVATE ${CMAKE_CURRENT_BINARY_DIR}/build-bergamot/src)
target_link_libraries(veil_bergamot bergamot-translator log)
target_link_options(veil_bergamot PRIVATE -Wl,--gc-sections)
