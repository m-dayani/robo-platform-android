cmake_minimum_required(VERSION 3.4.1)

add_library(native-lib SHARED
        native-lib.cpp
)

#include_directories(src/main/cpp/include/)

find_library(log-lib log)

#find_library(lib-boost lboost)

#add_library(lib-g2o SHARED IMPORTED)
#set_target_properties(lib-g2o PROPERTIES IMPORTED_LOCATION thirdparty/g2o/lib/libg2o.so)
#set_target_properties(lib-g2o PROPERTIES INCLUDE_DIRECTORIES /usr/include/boost)

#include_directories( /usr/include/boost lib-g2o/g2o)

#find_package(OpenCV 4.2.0 REQUIRED)


#set(g2o_src_DIR thirdparty/g2o )
#set(g2o_build_DIR thirdparty/g2o/build )
#file(MAKE_DIRECTORY ${lib_build_DIR})
#
#add_subdirectory(${g2o_src_DIR} ${g2o_build_DIR})
#
#add_library(lib_g2o STATIC IMPORTED)
#set_target_properties(lib_g2o PROPERTIES IMPORTED_LOCATION ${g2o_build_DIR}/${ANDROID_ABI}/lib_g2o.a)
#include_directories(${g2o_src_DIR}/g2o)

target_link_libraries(native-lib ${log-lib})