
/*
 * Copyright (C) NGINX, Inc.
 */

#ifndef _NXT_JAVA_REQUEST_H_INCLUDED_
#define _NXT_JAVA_REQUEST_H_INCLUDED_


#include <jni.h>
#include <nxt_unit_typedefs.h>


int nxt_java_initRequest(JNIEnv *env);

jobject nxt_java_newRequest(JNIEnv *env, nxt_unit_request_info_t *req);

#endif  /* _NXT_JAVA_REQUEST_H_INCLUDED_ */
