/*
 * JNITest.cpp
 *
 *  Created on: 12.09.2017
 *      Author: Viktor Csomor
 */

#include "JNITest.h"

#include "jni.h"

#include "test.h"

JNIEXPORT void JNICALL Java_JNITest_wait(JNIEnv * env, jclass clazz, jint sleepTime) {
	wait(sleepTime);
}
