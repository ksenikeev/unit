
/*
 * Copyright (C) NGINX, Inc.
 */

#ifndef _NXT_JAVA_CONTEXT_H_INCLUDED_
#define _NXT_JAVA_CONTEXT_H_INCLUDED_


#include <jni.h>


int nxt_java_initContext(JNIEnv *env);

void nxt_java_startContext(JNIEnv *env, const char *webapp,
    const char *servlet);

void nxt_java_service(JNIEnv *env, jobject jreq, jobject jresp);

#endif  /* _NXT_JAVA_CONTEXT_H_INCLUDED_ */

