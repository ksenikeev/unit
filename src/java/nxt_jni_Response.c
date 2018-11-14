
/*
 * Copyright (C) NGINX, Inc.
 */

#include <nxt_auto_config.h>

#include <nxt_unit.h>
#include <nxt_unit_response.h>
#include <jni.h>
#include <stdio.h>

#include "nxt_jni.h"
#include "nxt_jni_Response.h"
#include "nxt_jni_HeadersEnumeration.h"
#include "nxt_jni_HeaderNamesEnumeration.h"
#include "nxt_jni_OutputStream.h"
#include "nxt_jni_URLClassLoader.h"


static jclass     nxt_java_Response_class;
static jmethodID  nxt_java_Response_ctor;


static void JNICALL nxt_java_Response_addHeader(JNIEnv *env, jclass cls,
    jlong req_info_ptr, jstring name, jint name_len,
    jstring value, jint value_len);

static nxt_unit_request_info_t *nxt_java_get_response_info(
    jlong req_info_ptr, uint32_t extra_fields, uint32_t extra_data);

static void JNICALL nxt_java_Response_addIntHeader(JNIEnv *env, jclass cls,
    jlong req_info_ptr, jstring name, jint name_len, jint value);

static void nxt_java_add_int_header(nxt_unit_request_info_t *req,
    const char *name, uint8_t name_len, int value);

static jboolean JNICALL nxt_java_Response_containsHeader(JNIEnv *env,
    jclass cls, jlong req_info_ptr, jstring name, jint name_len);

static jstring JNICALL nxt_java_Response_getHeader(JNIEnv *env, jclass cls,
    jlong req_info_ptr, jstring name, jint name_len);

static jobject JNICALL nxt_java_Response_getHeaderNames(JNIEnv *env,
    jclass cls, jlong req_info_ptr);

static jobject JNICALL nxt_java_Response_getHeaders(JNIEnv *env, jclass cls,
    jlong req_info_ptr, jstring name, jint name_len);

static jint JNICALL nxt_java_Response_getStatus(JNIEnv *env, jclass cls,
    jlong req_info_ptr);

static void JNICALL nxt_java_Response_commit(JNIEnv *env, jclass cls,
    jlong req_info_ptr);

static void JNICALL nxt_java_Response_sendRedirect(JNIEnv *env, jclass cls,
    jlong req_info_ptr, jstring loc, jint loc_len);

static int nxt_java_response_set_header(jlong req_info_ptr,
    const char *name, jint name_len, const char *value, jint value_len);

static void JNICALL nxt_java_Response_setHeader(JNIEnv *env, jclass cls,
    jlong req_info_ptr, jstring name, jint name_len,
    jstring value, jint value_len);

static void JNICALL nxt_java_Response_setIntHeader(JNIEnv *env, jclass cls,
    jlong req_info_ptr, jstring name, jint name_len, jint value);

static void JNICALL nxt_java_Response_setStatus(JNIEnv *env, jclass cls,
    jlong req_info_ptr, jint sc);

static jstring JNICALL nxt_java_Response_getContentType(JNIEnv *env,
    jclass cls, jlong req_info_ptr);

static jboolean JNICALL nxt_java_Response_isCommitted(JNIEnv *env, jclass cls,
    jlong req_info_ptr);

static void JNICALL nxt_java_Response_reset(JNIEnv *env, jclass cls,
    jlong req_info_ptr);

static void JNICALL nxt_java_Response_resetBuffer(JNIEnv *env, jclass cls,
    jlong req_info_ptr);

static void JNICALL nxt_java_Response_setBufferSize(JNIEnv *env, jclass cls,
    jlong req_info_ptr, jint size);

static jint JNICALL nxt_java_Response_getBufferSize(JNIEnv *env, jclass cls,
    jlong req_info_ptr);

static void JNICALL nxt_java_Response_setContentLength(JNIEnv *env, jclass cls,
    jlong req_info_ptr, jlong len);

static void JNICALL nxt_java_Response_setContentType(JNIEnv *env, jclass cls,
    jlong req_info_ptr, jstring type, jint type_len);

static void JNICALL nxt_java_Response_log(JNIEnv *env, jclass cls,
    jlong req_info_ptr, jstring msg, jint msg_len);

static void JNICALL nxt_java_Response_trace(JNIEnv *env, jclass cls,
    jlong req_info_ptr, jstring msg, jint msg_len);

int
nxt_java_initResponse(JNIEnv *env, jobject cl)
{
    int     res;
    jclass  cls;

    cls = nxt_java_loadClass(env, cl, "nginx.unit.Response");
    if (cls == NULL) {
        return NXT_UNIT_ERROR;
    }

    nxt_java_Response_class = (*env)->NewGlobalRef(env, cls);
    (*env)->DeleteLocalRef(env, cls);
    cls = nxt_java_Response_class;

    nxt_java_Response_ctor = (*env)->GetMethodID(env, cls, "<init>",
                                                 "(JLnginx/unit/Request;)V");
    if (nxt_java_Response_ctor == NULL) {
        (*env)->DeleteGlobalRef(env, cls);
        return NXT_UNIT_ERROR;
    }

    JNINativeMethod resp_methods[] = {
        { (char *) "addHeader",
          (char *) "(JLjava/lang/String;ILjava/lang/String;I)V",
          nxt_java_Response_addHeader },

        { (char *) "addIntHeader",
          (char *) "(JLjava/lang/String;II)V",
          nxt_java_Response_addIntHeader },

        { (char *) "containsHeader",
          (char *) "(JLjava/lang/String;I)Z",
          nxt_java_Response_containsHeader },

        { (char *) "getHeader",
          (char *) "(JLjava/lang/String;I)Ljava/lang/String;",
          nxt_java_Response_getHeader },

        { (char *) "getHeaderNames",
          (char *) "(J)Ljava/util/Enumeration;",
          nxt_java_Response_getHeaderNames },

        { (char *) "getHeaders",
          (char *) "(JLjava/lang/String;I)Ljava/util/Enumeration;",
          nxt_java_Response_getHeaders },

        { (char *) "getStatus",
          (char *) "(J)I",
          nxt_java_Response_getStatus },

        { (char *) "commit",
          (char *) "(J)V",
          nxt_java_Response_commit },

        { (char *) "sendRedirect",
          (char *) "(JLjava/lang/String;I)V",
          nxt_java_Response_sendRedirect },

        { (char *) "setHeader",
          (char *) "(JLjava/lang/String;ILjava/lang/String;I)V",
          nxt_java_Response_setHeader },

        { (char *) "setIntHeader",
          (char *) "(JLjava/lang/String;II)V",
          nxt_java_Response_setIntHeader },

        { (char *) "setStatus",
          (char *) "(JI)V",
          nxt_java_Response_setStatus },

        { (char *) "getContentType",
          (char *) "(J)Ljava/lang/String;",
          nxt_java_Response_getContentType },

        { (char *) "isCommitted",
          (char *) "(J)Z",
          nxt_java_Response_isCommitted },

        { (char *) "reset",
          (char *) "(J)V",
          nxt_java_Response_reset },

        { (char *) "resetBuffer",
          (char *) "(J)V",
          nxt_java_Response_resetBuffer },

        { (char *) "setBufferSize",
          (char *) "(JI)V",
          nxt_java_Response_setBufferSize },

        { (char *) "getBufferSize",
          (char *) "(J)I",
          nxt_java_Response_getBufferSize },

        { (char *) "setContentLength",
          (char *) "(JJ)V",
          nxt_java_Response_setContentLength },

        { (char *) "setContentType",
          (char *) "(JLjava/lang/String;I)V",
          nxt_java_Response_setContentType },

        { (char *) "log",
          (char *) "(JLjava/lang/String;I)V",
          nxt_java_Response_log },

        { (char *) "trace",
          (char *) "(JLjava/lang/String;I)V",
          nxt_java_Response_trace },

    };

    res = (*env)->RegisterNatives(env, nxt_java_Response_class,
                                  resp_methods,
                                  sizeof(resp_methods) / sizeof(resp_methods[0]));

    nxt_unit_debug(NULL, "registered Response methods: %d", res);

    if (res != 0) {
        (*env)->DeleteGlobalRef(env, cls);
        return NXT_UNIT_ERROR;
    }

    return NXT_UNIT_OK;
}


jobject
nxt_java_newResponse(JNIEnv *env, nxt_unit_request_info_t *req, jobject jreq)
{
    return (*env)->NewObject(env, nxt_java_Response_class,
                             nxt_java_Response_ctor, (jlong) req, jreq);
}


static void JNICALL
nxt_java_Response_addHeader(JNIEnv *env, jclass cls, jlong req_info_ptr,
    jstring name, jint name_len, jstring value, jint value_len)
{
    int                      rc;
    const char               *name_str, *value_str;
    nxt_unit_request_info_t  *req;

    req = nxt_java_get_response_info(req_info_ptr, 1, name_len + value_len);
    if (req == NULL) {
        return;
    }

    name_str = (*env)->GetStringUTFChars(env, name, NULL);
    if (name_str == NULL) {
        return;
    }

    value_str = (*env)->GetStringUTFChars(env, value, NULL);
    if (value_str != NULL) {
        rc = nxt_unit_response_add_field(req, name_str, name_len,
                                         value_str, value_len);
        if (rc != NXT_UNIT_OK) {
            // throw
        }

        (*env)->ReleaseStringUTFChars(env, value, value_str);
    }

    (*env)->ReleaseStringUTFChars(env, name, name_str);
}


static nxt_unit_request_info_t *
nxt_java_get_response_info(jlong req_info_ptr, uint32_t extra_fields,
    uint32_t extra_data)
{
    int                      rc;
    char                     *p;
    uint32_t                 max_size;
    nxt_unit_buf_t           *buf;
    nxt_unit_request_info_t  *req;
    nxt_java_request_data_t  *data;

    req = (nxt_unit_request_info_t *) req_info_ptr;

    if (nxt_unit_response_is_sent(req)) {
        return NULL;
    }

    data = req->data;

    if (!nxt_unit_response_is_init(req)) {
        max_size = nxt_unit_buf_max();
        max_size = max_size < data->header_size ? max_size : data->header_size;

        rc = nxt_unit_response_init(req, 200, 16, max_size);
        if (rc != NXT_UNIT_OK) {
            return NULL;
        }
    }

    buf = req->response_buf;

    if (extra_fields > req->response_max_fields
                       - req->response->fields_count
        || extra_data > (uint32_t) (buf->end - buf->free))
    {
        p = buf->start + req->response_max_fields * sizeof(nxt_unit_field_t);

        max_size = 2 * (buf->end - p);
        if (max_size > nxt_unit_buf_max()) {
            return NULL;
        }

        rc = nxt_unit_response_realloc(req, 2 * req->response_max_fields,
                                       max_size);
        if (rc != NXT_UNIT_OK) {
            return NULL;
        }
    }

    return req;
}


static void JNICALL
nxt_java_Response_addIntHeader(JNIEnv *env, jclass cls, jlong req_info_ptr,
    jstring name, jint name_len, jint value)
{
    const char               *name_str;
    nxt_unit_request_info_t  *req;

    req = nxt_java_get_response_info(req_info_ptr, 1, name_len + 40);
    if (req == NULL) {
        return;
    }

    name_str = (*env)->GetStringUTFChars(env, name, NULL);
    if (name_str == NULL) {
        return;
    }

    nxt_java_add_int_header(req, name_str, name_len, value);

    (*env)->ReleaseStringUTFChars(env, name, name_str);
}


static void
nxt_java_add_int_header(nxt_unit_request_info_t *req, const char *name,
    uint8_t name_len, int value)
{
    char                 *p;
    nxt_unit_field_t     *f;
    nxt_unit_response_t  *resp;

    resp = req->response;

    f = resp->fields + resp->fields_count;
    p = req->response_buf->free;

    f->hash = nxt_unit_field_hash(name, name_len);
    f->skip = 0;
    f->name_length = name_len;

    nxt_unit_sptr_set(&f->name, p);
    memcpy(p, name, name_len);
    p += name_len;

    nxt_unit_sptr_set(&f->value, p);
    f->value_length = snprintf(p, 40, "%d", (int) value);
    p += f->value_length + 1;

    resp->fields_count++;
    req->response_buf->free = p;

}


static jboolean JNICALL
nxt_java_Response_containsHeader(JNIEnv *env,
    jclass cls, jlong req_info_ptr, jstring name, jint name_len)
{
    jboolean                 res;
    const char               *name_str;
    nxt_unit_response_t      *resp;
    nxt_unit_request_info_t  *req;

    req = (nxt_unit_request_info_t *) req_info_ptr;

    if (!nxt_unit_response_is_init(req)) {
        nxt_unit_req_debug(req, "containsHeader: response is not initialized");
        return 0;
    }

    if (nxt_unit_response_is_sent(req)) {
        nxt_unit_req_debug(req, "containsHeader: response already sent");
        return 0;
    }

    name_str = (*env)->GetStringUTFChars(env, name, NULL);
    if (name_str == NULL) {
        nxt_unit_req_debug(req, "containsHeader: failed to get name");
        return 0;
    }

    resp = req->response;

    res = nxt_java_findHeader(resp->fields,
                              resp->fields + resp->fields_count,
                              name_str, name_len) != NULL;

    (*env)->ReleaseStringUTFChars(env, name, name_str);

    return res;
}


static jstring JNICALL
nxt_java_Response_getHeader(JNIEnv *env, jclass cls, jlong req_info_ptr,
    jstring name, jint name_len)
{
    const char               *name_str;
    nxt_unit_field_t         *f;
    nxt_unit_request_info_t  *req;

    req = (nxt_unit_request_info_t *) req_info_ptr;

    if (!nxt_unit_response_is_init(req)) {
        nxt_unit_req_debug(req, "getHeader: response is not initialized");
        return NULL;
    }

    if (nxt_unit_response_is_sent(req)) {
        nxt_unit_req_debug(req, "getHeader: response already sent");
        return NULL;
    }

    name_str = (*env)->GetStringUTFChars(env, name, NULL);
    if (name_str == NULL) {
        nxt_unit_req_debug(req, "getHeader: failed to get name");
        return NULL;
    }

    f = nxt_java_findHeader(req->response->fields,
                            req->response->fields + req->response->fields_count,
                            name_str, name_len);

    (*env)->ReleaseStringUTFChars(env, name, name_str);

    if (f == NULL) {
        return NULL;
    }

    return nxt_java_newString(env, nxt_unit_sptr_get(&f->value),
                              f->value_length);
}


static jobject JNICALL
nxt_java_Response_getHeaderNames(JNIEnv *env, jclass cls, jlong req_info_ptr)
{
    nxt_unit_request_info_t  *req;

    req = (nxt_unit_request_info_t *) req_info_ptr;

    if (!nxt_unit_response_is_init(req)) {
        nxt_unit_req_debug(req, "getHeaderNames: response is not initialized");
        return NULL;
    }

    if (nxt_unit_response_is_sent(req)) {
        nxt_unit_req_debug(req, "getHeaderNames: response already sent");
        return NULL;
    }

    return nxt_java_newHeaderNamesEnumeration(env, req->response->fields,
                                              req->response->fields_count);
}


static jobject JNICALL
nxt_java_Response_getHeaders(JNIEnv *env, jclass cls,
    jlong req_info_ptr, jstring name, jint name_len)
{
    const char               *name_str;
    nxt_unit_field_t         *f;
    nxt_unit_response_t      *resp;
    nxt_unit_request_info_t  *req;

    req = (nxt_unit_request_info_t *) req_info_ptr;

    if (!nxt_unit_response_is_init(req)) {
        nxt_unit_req_debug(req, "getHeaders: response is not initialized");
        return NULL;
    }

    if (nxt_unit_response_is_sent(req)) {
        nxt_unit_req_debug(req, "getHeaders: response already sent");
        return NULL;
    }

    resp = req->response;

    name_str = (*env)->GetStringUTFChars(env, name, NULL);
    if (name_str == NULL) {
        nxt_unit_req_debug(req, "getHeaders: failed to get name");
        return NULL;
    }

    f = nxt_java_findHeader(resp->fields, resp->fields + resp->fields_count,
                            name_str, name_len);

    (*env)->ReleaseStringUTFChars(env, name, name_str);

    if (f == NULL) {
        f = resp->fields + resp->fields_count;
    }

    return nxt_java_newHeadersEnumeration(env, resp->fields, resp->fields_count,
                                          f - resp->fields);
}


static jint JNICALL
nxt_java_Response_getStatus(JNIEnv *env, jclass cls, jlong req_info_ptr)
{
    nxt_unit_request_info_t  *req;

    req = (nxt_unit_request_info_t *) req_info_ptr;

    if (!nxt_unit_response_is_init(req)) {
        nxt_unit_req_debug(req, "getStatus: response is not initialized");
        return 200;
    }

    if (nxt_unit_response_is_sent(req)) {
        nxt_unit_req_debug(req, "getStatus: response already sent");
        return 200;
    }

    return req->response->status;
}


static void JNICALL
nxt_java_Response_commit(JNIEnv *env, jclass cls, jlong req_info_ptr)
{
    nxt_unit_request_info_t  *req = (nxt_unit_request_info_t *) req_info_ptr;

    nxt_java_OutputStream_flush_buf(env, req);
}


static void JNICALL
nxt_java_Response_sendRedirect(JNIEnv *env, jclass cls,
    jlong req_info_ptr, jstring loc, jint loc_len)
{
    int                      rc;
    const char               *loc_str;
    nxt_unit_request_info_t  *req = (nxt_unit_request_info_t *) req_info_ptr;

    static const char        location[] = "Location";
    static const uint32_t    location_len = sizeof(location) - 1;

    if (nxt_unit_response_is_sent(req)) {
        nxt_java_throw_IllegalStateException(env, "Response already sent");

        return;
    }

    loc_str = (*env)->GetStringUTFChars(env, loc, NULL);
    if (loc_str == NULL) {
        return;
    }

    req = nxt_java_get_response_info(req_info_ptr, 0, 0);
    if (req != NULL) {
        (*env)->ReleaseStringUTFChars(env, loc, loc_str);

        return;
    }

    req->response->status = 302;

    // TODO transform loc to absolute URL

    rc = nxt_java_response_set_header(req_info_ptr, location, location_len,
                                      loc_str, loc_len);
    if (rc != NXT_UNIT_OK) {
        // throw
    }

    (*env)->ReleaseStringUTFChars(env, loc, loc_str);

    nxt_unit_response_send(req);
}


static int
nxt_java_response_set_header(jlong req_info_ptr,
    const char *name, jint name_len, const char *value, jint value_len)
{
    int                      add_field;
    char                     *dst;
    nxt_unit_field_t         *f, *e;
    nxt_unit_response_t      *resp;
    nxt_unit_request_info_t  *req;

    req = nxt_java_get_response_info(req_info_ptr, 0, 0);
    if (req == NULL) {
        return NXT_UNIT_ERROR;
    }

    resp = req->response;

    f = resp->fields;
    e = f + resp->fields_count;

    add_field = 1;

    for ( ;; ) {
        f = nxt_java_findHeader(f, e, name, name_len);
        if (f == NULL) {
            break;
        }

        if (add_field && f->value_length <= (uint32_t) value_len) {
            dst = nxt_unit_sptr_get(&f->value);
            memcpy(dst, value, value_len);
            dst[value_len] = '\0';
            f->value_length = value_len;

            add_field = 0;

        } else {
            f->skip = 1;
        }

        ++f;
    }

    if (!add_field) {
        return NXT_UNIT_OK;
    }

    req = nxt_java_get_response_info(req_info_ptr, 1, name_len + value_len);
    if (req == NULL) {
        return NXT_UNIT_ERROR;
    }

    return nxt_unit_response_add_field(req, name, name_len, value, value_len);
}


static void JNICALL
nxt_java_Response_setHeader(JNIEnv *env, jclass cls,
    jlong req_info_ptr, jstring name, jint name_len,
    jstring value, jint value_len)
{
    int         rc;
    const char  *name_str, *value_str;

    name_str = (*env)->GetStringUTFChars(env, name, NULL);
    if (name_str == NULL) {
        return;
    }

    value_str = (*env)->GetStringUTFChars(env, value, NULL);
    if (value_str == NULL) {
        (*env)->ReleaseStringUTFChars(env, name, name_str);

        return;
    }

    rc = nxt_java_response_set_header(req_info_ptr, name_str, name_len,
                                      value_str, value_len);
    if (rc != NXT_UNIT_OK) {
        // throw
    }

    (*env)->ReleaseStringUTFChars(env, value, value_str);
    (*env)->ReleaseStringUTFChars(env, name, name_str);
}


static void JNICALL
nxt_java_Response_setIntHeader(JNIEnv *env, jclass cls,
    jlong req_info_ptr, jstring name, jint name_len, jint value)
{
    int         value_len, rc;
    char        value_str[40];
    const char  *name_str;

    value_len = snprintf(value_str, sizeof(value_str), "%d", (int) value);

    name_str = (*env)->GetStringUTFChars(env, name, NULL);
    if (name_str == NULL) {
        return;
    }

    rc = nxt_java_response_set_header(req_info_ptr, name_str, name_len,
                                      value_str, value_len);
    if (rc != NXT_UNIT_OK) {
        // throw
    }

    (*env)->ReleaseStringUTFChars(env, name, name_str);
}


static void JNICALL
nxt_java_Response_setStatus(JNIEnv *env, jclass cls, jlong req_info_ptr,
    jint sc)
{
    nxt_unit_request_info_t  *req;

    req = nxt_java_get_response_info(req_info_ptr, 0, 0);
    if (req == NULL) {
        return;
    }

    req->response->status = sc;
}


static jstring JNICALL
nxt_java_Response_getContentType(JNIEnv *env, jclass cls, jlong req_info_ptr)
{
    nxt_unit_field_t         *f;
    nxt_unit_request_info_t  *req;

    req = (nxt_unit_request_info_t *) req_info_ptr;

    if (!nxt_unit_response_is_init(req)) {
        nxt_unit_req_debug(req, "getContentType: response is not initialized");
        return NULL;
    }

    if (nxt_unit_response_is_sent(req)) {
        nxt_unit_req_debug(req, "getContentType: response already sent");
        return NULL;
    }

    f = nxt_java_findHeader(req->response->fields,
                            req->response->fields + req->response->fields_count,
                            "Content-Type", sizeof("Content-Type") - 1);

    if (f == NULL) {
        return NULL;
    }

    return nxt_java_newString(env, nxt_unit_sptr_get(&f->value),
                              f->value_length);
}


static jboolean JNICALL
nxt_java_Response_isCommitted(JNIEnv *env, jclass cls, jlong req_info_ptr)
{
    nxt_unit_request_info_t  *req = (nxt_unit_request_info_t *) req_info_ptr;

    if (nxt_unit_response_is_sent(req)) {
        return 1;
    }

    return 0;
}


static void JNICALL
nxt_java_Response_reset(JNIEnv *env, jclass cls, jlong req_info_ptr)
{
    nxt_unit_buf_t           *buf;
    nxt_unit_request_info_t  *req = (nxt_unit_request_info_t *) req_info_ptr;
    nxt_java_request_data_t  *data = req->data;

    if (nxt_unit_response_is_sent(req)) {
        nxt_java_throw_IllegalStateException(env, "Response already sent");

        return;
    }

    if (data->buf != NULL && data->buf->free > data->buf->start) {
        data->buf->free = data->buf->start;
    }

    if (nxt_unit_response_is_init(req)) {
        req->response->status = 200;
        req->response->fields_count = 0;

        buf = req->response_buf;

        buf->free = buf->start + req->response_max_fields
                                  * sizeof(nxt_unit_field_t);
    }
}


static void JNICALL
nxt_java_Response_resetBuffer(JNIEnv *env, jclass cls, jlong req_info_ptr)
{
    nxt_unit_request_info_t  *req = (nxt_unit_request_info_t *) req_info_ptr;
    nxt_java_request_data_t  *data = req->data;

    if (data->buf != NULL && data->buf->free > data->buf->start) {
        data->buf->free = data->buf->start;
    }
}


static void JNICALL
nxt_java_Response_setBufferSize(JNIEnv *env, jclass cls, jlong req_info_ptr,
    jint size)
{
    nxt_unit_request_info_t  *req = (nxt_unit_request_info_t *) req_info_ptr;
    nxt_java_request_data_t  *data = req->data;

    if (data->buf_size == (uint32_t) size) {
        return;
    }

    if (data->buf != NULL && data->buf->free > data->buf->start) {
        nxt_java_throw_IllegalStateException(env, "Buffer is not empty");

        return;
    }

    data->buf_size = size;

    if (data->buf_size > nxt_unit_buf_max()) {
        data->buf_size = nxt_unit_buf_max();
    }

    if (data->buf != NULL
        && data->buf->end - data->buf->start < data->buf_size)
    {
        nxt_unit_buf_free(data->buf);

        data->buf = NULL;
    }
}


static jint JNICALL
nxt_java_Response_getBufferSize(JNIEnv *env, jclass cls, jlong req_info_ptr)
{
    nxt_unit_request_info_t  *req = (nxt_unit_request_info_t *) req_info_ptr;
    nxt_java_request_data_t  *data = req->data;

    return data->buf_size;
}


static void JNICALL
nxt_java_Response_setContentLength(JNIEnv *env, jclass cls, jlong req_info_ptr,
    jlong len)
{
    nxt_unit_request_info_t  *req;

    req = nxt_java_get_response_info(req_info_ptr, 0, 0);
    if (req == NULL) {
        return;
    }

    req->response->content_length = len;
}


static void JNICALL
nxt_java_Response_setContentType(JNIEnv *env, jclass cls, jlong req_info_ptr,
    jstring type, jint type_len)
{
    int                    rc;
    const char             *type_str;

    static const char      content_type[] = "Content-Type";
    static const uint32_t  content_type_len = sizeof(content_type) - 1;

    type_str = (*env)->GetStringUTFChars(env, type, NULL);
    if (type_str == NULL) {
        return;
    }

    rc = nxt_java_response_set_header(req_info_ptr,
                                      content_type, content_type_len,
                                      type_str, type_len);
    if (rc != NXT_UNIT_OK) {
        // throw
    }

    (*env)->ReleaseStringUTFChars(env, type, type_str);
}

static void JNICALL
nxt_java_Response_log(JNIEnv *env, jclass cls, jlong req_info_ptr, jstring msg,
    jint msg_len)
{
    const char               *msg_str;
    nxt_unit_request_info_t  *req = (nxt_unit_request_info_t *) req_info_ptr;

    msg_str = (*env)->GetStringUTFChars(env, msg, NULL);
    if (msg_str == NULL) {
        return;
    }

    nxt_unit_req_log(req, NXT_UNIT_LOG_INFO, "%.*s", msg_len, msg_str);

    (*env)->ReleaseStringUTFChars(env, msg, msg_str);
}

static void JNICALL
nxt_java_Response_trace(JNIEnv *env, jclass cls, jlong req_info_ptr, jstring msg,
    jint msg_len)
{
#if (NXT_DEBUG)
    const char               *msg_str;
    nxt_unit_request_info_t  *req = (nxt_unit_request_info_t *) req_info_ptr;

    msg_str = (*env)->GetStringUTFChars(env, msg, NULL);
    if (msg_str == NULL) {
        return;
    }

    nxt_unit_req_debug(req, "%.*s", msg_len, msg_str);

    (*env)->ReleaseStringUTFChars(env, msg, msg_str);
#endif
}

