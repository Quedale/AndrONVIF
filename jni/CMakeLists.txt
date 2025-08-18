cmake_minimum_required(VERSION 3.18.1)

project("AndrONVIF" LANGUAGES C CXX)

if(NOT DEFINED GSTREAMER_ROOT_ANDROID)
    set(GSTREAMER_ROOT_ANDROID "${CMAKE_CURRENT_SOURCE_DIR}/subprojects/gstreamer-1.0-android-universal-1.26.5")
endif()

#Set cmake variables as environment so that autogen.sh inherit the environment
get_cmake_property(_variableNames VARIABLES)
list(SORT _variableNames)
foreach (_variableName ${_variableNames})
    set(ENV{${_variableName}} "${${_variableName}}")
endforeach()

#Execute autogen.sh to download dependencies.
execute_process (COMMAND bash -c " \
    PATH=${CMAKE_COMMAND}:$PATH \
    ${CMAKE_CURRENT_SOURCE_DIR}/autogen.sh
    " RESULT_VARIABLE ret)
if(ret GREATER "0")
    message( FATAL_ERROR "autogen.sh script failed.")
endif()

#Define library
add_library(andronvif SHARED andronvif.c)

#########################################
# Gstreamre specific config
#########################################
if(ANDROID_ABI STREQUAL "armeabi")
    set(GSTREAMER_ROOT "${GSTREAMER_ROOT_ANDROID}/arm")
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(GSTREAMER_ROOT "${GSTREAMER_ROOT_ANDROID}/armv7")
elseif(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GSTREAMER_ROOT "${GSTREAMER_ROOT_ANDROID}/arm64")
elseif(ANDROID_ABI STREQUAL "x86")
    set(GSTREAMER_ROOT "${GSTREAMER_ROOT_ANDROID}/x86")
elseif(ANDROID_ABI STREQUAL "x86_64")
    set(GSTREAMER_ROOT "${GSTREAMER_ROOT_ANDROID}/x86_64")
else()
    message(FATAL_ERROR "Target arch ABI not supported: ${ANDROID_ABI}")
endif()

list(APPEND CMAKE_MODULE_PATH "${GSTREAMER_ROOT}/share/cmake")

set(GSTREAMER_NDK_BUILD_PATH  "${GSTREAMER_ROOT}/share/gst-android/ndk-build/")
set(GSTREAMER_PLUGINS         coreelements)
find_library(LOG_LIB log REQUIRED)
find_package(GStreamerMobile COMPONENTS ${GSTREAMER_PLUGINS} fonts REQUIRED)

target_link_libraries(andronvif
    PUBLIC
        GStreamer::mobile
        ${ANDROID_LIB}
        ${LOG_LIB}
)
set_target_properties(andronvif
    PROPERTIES
        C_VISIBILITY_PRESET hidden
        CXX_VISIBILITY_PRESET hidden
)
