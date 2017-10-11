/*
 * test.h
 *
 *  Created on: 12.09.2017
 *      Author: Viktor Csomor
 */

#ifndef TEST_H
#define TEST_H
#ifdef __cplusplus
extern "C" {
#endif

#if defined _WIN32 || defined __CYGWIN__
#ifdef BUILDING_DLL
#define DLL_PUBLIC __declspec(dllexport)
#else
#define DLL_PUBLIC __declspec(dllimport)
#endif
#else
#if __GNUC__ >= 4
#define DLL_PUBLIC __attribute__ ((visibility ("default")))
#else
#define DLL_PUBLIC
#endif
#endif

DLL_PUBLIC void doStuff(int seconds);

#ifdef __cplusplus
}
#endif
#endif /* TEST_H */
