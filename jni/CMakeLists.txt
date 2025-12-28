cmake_minimum_required(VERSION 3.18.1)

project("andronvif" LANGUAGES C CXX)

if(NOT DEFINED SUBPROJECTS_ROOT)
    set(SUBPROJECTS_ROOT "${CMAKE_SOURCE_DIR}/subprojects")
endif()

#########################################
# Gstreamer specific config
#########################################
if(NOT DEFINED GSTREAMER_ROOT_ANDROID)
    set(GSTREAMER_ROOT_ANDROID "${SUBPROJECTS_ROOT}/gstreamer-android")
endif()
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

#########################################
# AndrONVIF specific config
#########################################
#Set cmake variables as environment so that autogen.sh inherit the environment
get_cmake_property(_variableNames VARIABLES)
list(SORT _variableNames)
foreach (_variableName ${_variableNames})
    set(ENV{${_variableName}} "${${_variableName}}")
endforeach()
set(ENV{TARGET_TOOLCHAIN} "${ANDROID_TOOLCHAIN_ROOT}") #original x86_64-linux-android
set(ENV{TARGET_TARGET} "${CMAKE_ASM_COMPILER_TARGET}") #original x86_64-linux-android
set(ENV{TARGET_API} "${ANDROID_NATIVE_API_LEVEL}")
set(ENV{TARGET_AR} "${ANDROID_AR}")
#set(ENV{TARGET_AS} "${}")?? #original $CC but value is empty
set(ENV{TARGET_CC} "${CMAKE_C_COMPILER} --target=${CMAKE_C_COMPILER_TARGET}")
set(ENV{TARGET_CXX} "${CMAKE_CXX_COMPILER} --target=${CMAKE_CXX_COMPILER_TARGET}")
set(ENV{TARGET_LD} "${CMAKE_LINKER}")
set(ENV{TARGET_RANLIB} "${CMAKE_RANLIB}")
set(ENV{TARGET_STRIP} "${CMAKE_STRIP}")
set(ENV{TARGET_CHOST} "${ANDROID_LLVM_TRIPLE}")

#Execute autogen.sh to download dependencies.
execute_process (COMMAND bash -c " \
    PATH=${CMAKE_COMMAND}:$PATH \
    ${CMAKE_CURRENT_SOURCE_DIR}/autogen.sh
    " RESULT_VARIABLE ret)
if(ret GREATER "0")
    message( FATAL_ERROR "autogen.sh script failed.")
endif()


list(APPEND CMAKE_MODULE_PATH "${GSTREAMER_ROOT}/share/cmake")
set(GSTREAMER_NDK_BUILD_PATH  "${GSTREAMER_ROOT}/share/gst-android/ndk-build/")
include("${GSTREAMER_NDK_BUILD_PATH}/plugins.cmake")
set(GSTREAMER_PLUGINS         ${GSTREAMER_PLUGINS_EFFECTS} ${GSTREAMER_PLUGINS_CORE} ${GSTREAMER_PLUGINS_PLAYBACK} ${GSTREAMER_PLUGINS_CODECS} ${GSTREAMER_PLUGINS_NET} ${GSTREAMER_PLUGINS_SYS})
set(GStreamer_EXTRA_DEPS gstreamer-video-1.0)
set(G_IO_MODULES openssl)
find_library(LOG_LIB log REQUIRED)
find_library(ANDROID_LIB android REQUIRED)
find_package(GStreamerMobile COMPONENTS ${GSTREAMER_PLUGINS} fonts REQUIRED)

#Define library
add_library(${CMAKE_PROJECT_NAME} SHARED
        andronvif.c
        andronvif_device.c
        andronvif_player.c
        jniobject.c
        onvif_discovery_service.c
        onvif_probmatch.c)

#16KB Page fix
target_link_options(${CMAKE_PROJECT_NAME} PRIVATE "-Wl,-z,max-page-size=16384")

add_library(cutils STATIC IMPORTED)
set_target_properties(cutils PROPERTIES IMPORTED_LOCATION ${SUBPROJECTS_ROOT}/CUtils/dist/lib/libcutils.a)
add_library(libntlm STATIC IMPORTED)
set_target_properties(libntlm PROPERTIES IMPORTED_LOCATION ${SUBPROJECTS_ROOT}/libntlm-1.8/dist/lib/libntlm.a)
add_library(onvifsoap STATIC IMPORTED)
set_target_properties(onvifsoap PROPERTIES IMPORTED_LOCATION ${SUBPROJECTS_ROOT}/OnvifSoapLib/dist/lib/libonvifsoap.a)

add_library(glib STATIC IMPORTED)
set_target_properties(glib PROPERTIES IMPORTED_LOCATION ${GSTREAMER_ROOT}/lib/libglib-2.0.a)
add_library(gobject STATIC IMPORTED)
set_target_properties(gobject PROPERTIES IMPORTED_LOCATION ${GSTREAMER_ROOT}/lib/libgobject-2.0.a)
add_library(ssl STATIC IMPORTED)
set_target_properties(ssl PROPERTIES IMPORTED_LOCATION ${GSTREAMER_ROOT}/lib/libssl.a)
add_library(crypto STATIC IMPORTED)
set_target_properties(crypto PROPERTIES IMPORTED_LOCATION ${GSTREAMER_ROOT}/lib/libcrypto.a)

target_include_directories(${CMAKE_PROJECT_NAME}
        PRIVATE
        ${GSTREAMER_ROOT}/include
        ${GSTREAMER_ROOT}/include/openssl
        ${GSTREAMER_ROOT}/include/glib-2.0
        ${GSTREAMER_ROOT}/lib/glib-2.0/include
        ${SUBPROJECTS_ROOT}/OnvifSoapLib/dist/include
        ${SUBPROJECTS_ROOT}/CUtils/dist/include
        ${SUBPROJECTS_ROOT}/libntlm-1.8/dist/include
)

target_link_libraries(${CMAKE_PROJECT_NAME}
    PRIVATE
    GStreamer::mobile
    cutils
    libntlm
    glib
    gobject
    onvifsoap
    ssl
    crypto
    ${ANDROID_LIB}
    ${LOG_LIB}
)

set_target_properties(${CMAKE_PROJECT_NAME}
    PROPERTIES
    C_VISIBILITY_PRESET hidden
    CXX_VISIBILITY_PRESET hidden
)
