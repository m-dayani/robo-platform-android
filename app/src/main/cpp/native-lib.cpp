//
// Created by root on 12/7/23.
//

#include <jni.h>
#include <string>
//#include "regex.hpp"
//#include "g2o/core/base_binary_edge.h"
//#include "lib_g2o/core/base_binary_edge.h"
#include "thirdparty/apriltag/apriltag.h"

extern "C" JNIEXPORT jstring JNICALL
Java_com_dayani_m_roboplatform_NativeTestActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

