
/*
 * Copyright (C) NGINX, Inc.
 */

#ifndef _NXT_JAVA_URLCLASSLOADER_H_INCLUDED_
#define _NXT_JAVA_URLCLASSLOADER_H_INCLUDED_


#include <jni.h>


int nxt_java_initURLClassLoader(JNIEnv *env);

jobject nxt_java_newURLClassLoader(JNIEnv *env, int url_count, char **urls);

jobjectArray nxt_java_newURls(JNIEnv *env, int url_count, char **urls);

jclass nxt_java_loadClass(JNIEnv *env, jobject cl, const char *name);

#endif  /* _NXT_JAVA_URLCLASSLOADER_H_INCLUDED_ */

