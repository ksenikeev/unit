
/*
 * Copyright (C) NGINX, Inc.
 */


#include <jni.h>

#include <nxt_main.h>
#include <nxt_runtime.h>
#include <nxt_router.h>
#include <nxt_unit.h>
#include <nxt_unit_field.h>
#include <nxt_unit_request.h>
#include <nxt_unit_response.h>

#include <java/nxt_jni.h>

#include "java/nxt_jni_Context.h"
#include "java/nxt_jni_Request.h"
#include "java/nxt_jni_Response.h"
#include "java/nxt_jni_InputStream.h"
#include "java/nxt_jni_OutputStream.h"

#include "nxt_java_jars.h"

static nxt_int_t nxt_java_init(nxt_task_t *task, nxt_common_app_conf_t *conf);
static void nxt_java_request_handler(nxt_unit_request_info_t *req);

static uint32_t  compat[] = {
    NXT_VERNUM, NXT_DEBUG,
};


#define STR1(x)  #x
#define STR(x) STR1(x)

NXT_EXPORT nxt_app_module_t  nxt_app_module = {
    sizeof(compat),
    compat,
    nxt_string("java"),
    STR(NXT_JAVA_VERSION),
    nxt_java_init,
};


static nxt_int_t
nxt_java_init(nxt_task_t *task, nxt_common_app_conf_t *conf)
{
    jint                 rc;
    char                 *opt, *slash;
    JavaVM               *jvm;
    JNIEnv               *env;
    nxt_str_t            str;
    nxt_int_t            opt_len, modules_len;
    nxt_uint_t           i;
    const char           **jar;
    JavaVMOption         *jvm_opt;
    nxt_runtime_t        *rt;
    JavaVMInitArgs       jvm_args;
    nxt_unit_ctx_t       *ctx;
    nxt_unit_init_t      java_init;
    nxt_conf_value_t     *value;
    nxt_java_app_conf_t  *c;

    static const char  OPT_CLASS_PATH[] = "-Djava.class.path=";

    //setenv("ASAN_OPTIONS", "handle_segv=0", 1);

    jvm_args.version = JNI_VERSION_1_6;
    jvm_args.nOptions = 1;
    jvm_args.ignoreUnrecognized = 0;

    c = &conf->u.java;

    if (c->options != NULL) {
        jvm_args.nOptions += nxt_conf_array_count(c->options);
    }

    jvm_opt = malloc(jvm_args.nOptions * sizeof(JavaVMOption));
    if (jvm_opt == NULL) {
        nxt_alert(task, "failed to allocate jvm_opt");
        return NXT_ERROR;
    }

    jvm_args.options = jvm_opt;

    rt = task->thread->runtime;

    slash = strrchr(rt->modules, '/');
    modules_len = slash == NULL ? nxt_strlen(rt->modules) : slash - rt->modules;

    opt_len = nxt_length(OPT_CLASS_PATH) + 1;

    for (jar = nxt_java_jars; *jar != NULL; jar++) {
        opt_len += modules_len + nxt_length("/") + nxt_strlen(*jar) +
                   nxt_length(":");
    }

    if (c->classpath.length > 0) {
        opt_len += 1 + c->classpath.length;
    }

    opt = malloc(opt_len);
    if (opt == NULL) {
        nxt_alert(task, "failed to allocate buffer for classpath");

        return NXT_ERROR;
    }

    jvm_opt[0].optionString = opt;

    opt = nxt_cpymem(opt, OPT_CLASS_PATH, nxt_length(OPT_CLASS_PATH));

    for (jar = nxt_java_jars; *jar != NULL; jar++) {
        opt = nxt_cpymem(opt, rt->modules, modules_len);
        *opt++ = '/';
        opt = nxt_cpymem(opt, *jar, nxt_strlen(*jar));
        *opt++ = ':';
    }

    if (c->classpath.length > 0) {
        *opt++ = ':';

        opt = nxt_cpymem(opt, c->classpath.start, c->classpath.length);
    }

    *opt++ = '\0';

    nxt_debug(task, "opt[0]=%s", jvm_opt[0].optionString);

/*
    jvm_opt[1].optionString = (char *) "-Xcheck:jni";
    jvm_opt[2].optionString = (char *) "-verbose:jni";
*/

    if (c->options != NULL) {

        for (i = 0; /* void */ ; i++) {
            value = nxt_conf_get_array_element(c->options, i);
            if (value == NULL) {
                break;
            }

            nxt_conf_get_string(value, &str);

            opt = malloc(str.length + 1);
            if (opt == NULL) {
                nxt_alert(task, "failed to allocate jvm_opt");
                return NXT_ERROR;
            }

            memcpy(opt, str.start, str.length);
            opt[str.length] = '\0';

            jvm_opt[1 + i].optionString = opt;
        }
    }

    rc = JNI_CreateJavaVM(&jvm, (void **) &env, &jvm_args);
    if (rc != JNI_OK) {
        nxt_alert(task, "failed to create Java VM: %d", (int) rc);
        return NXT_ERROR;
    }

    rc = nxt_java_initContext(env);
    if (rc != NXT_UNIT_OK) {
        nxt_alert(task, "nxt_java_initContext() failed");
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionDescribe(env);
        }
        return NXT_ERROR;
    }

    rc = nxt_java_initRequest(env);
    if (rc != NXT_UNIT_OK) {
        nxt_alert(task, "nxt_java_initRequest() failed");
        return NXT_ERROR;
    }

    rc = nxt_java_initResponse(env);
    if (rc != NXT_UNIT_OK) {
        nxt_alert(task, "nxt_java_initResponse() failed");
        return NXT_ERROR;
    }

    rc = nxt_java_initInputStream(env);
    if (rc != NXT_UNIT_OK) {
        nxt_alert(task, "nxt_java_initInputStream() failed");
        return NXT_ERROR;
    }

    rc = nxt_java_initOutputStream(env);
    if (rc != NXT_UNIT_OK) {
        nxt_alert(task, "nxt_java_initOutputStream() failed");
        return NXT_ERROR;
    }

    nxt_java_jni_init(env);
    if (rc != NXT_UNIT_OK) {
        nxt_alert(task, "nxt_java_jni_init() failed");
        return NXT_ERROR;
    }

    nxt_java_startContext(env, c->webapp, c->servlet);

    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionDescribe(env);
    }

    nxt_unit_default_init(task, &java_init);

    java_init.callbacks.request_handler = nxt_java_request_handler;
    java_init.request_data_size = sizeof(nxt_java_request_data_t);
    java_init.data = env;

    ctx = nxt_unit_init(&java_init);
    if (nxt_slow_path(ctx == NULL)) {
        nxt_alert(task, "nxt_unit_init() failed");
        return NXT_ERROR;
    }

    rc = nxt_unit_run(ctx);
    if (nxt_slow_path(rc != NXT_UNIT_OK)) {
        /* TODO report error */
    }

    nxt_unit_done(ctx);

    (*jvm)->DestroyJavaVM(jvm);

    exit(0);

    return NXT_OK;
}


static void
nxt_java_request_handler(nxt_unit_request_info_t *req)
{
    JNIEnv                   *env = req->unit->data;
    nxt_java_request_data_t  *data = req->data;

    jobject jreq = nxt_java_newRequest(env, req);

    if (jreq == NULL) {
        nxt_unit_req_alert(req, "failed to create Request instance");

        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);
        }

        nxt_unit_request_done(req, NXT_UNIT_ERROR);
        return;
    }

    jobject jresp = nxt_java_newResponse(env, req, jreq);

    if (jresp == NULL) {
        nxt_unit_req_alert(req, "failed to create Response instance");

        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);
        }

        (*env)->DeleteLocalRef(env, jreq);

        nxt_unit_request_done(req, NXT_UNIT_ERROR);
        return;
    }

    data->header_size = 10 * 1024;
    data->buf_size = 32 * 1024; /* from Jetty */
    data->buf = NULL;

    nxt_unit_request_group_dup_fields(req);

    nxt_java_service(env, jreq, jresp);
/*
    (*env)->CallVoidMethod(env, java_servlet->servlet, java_servlet->service,
        jreq, jresp);
*/

    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
    }

    if (!nxt_unit_response_is_init(req)) {
        nxt_unit_response_init(req, 200, 0, 0);
    }

    if (!nxt_unit_response_is_sent(req)) {
        nxt_unit_response_send(req);
    }

    if (data->buf != NULL) {
        nxt_unit_buf_send(data->buf);

        data->buf = NULL;
    }

    (*env)->DeleteLocalRef(env, jresp);
    (*env)->DeleteLocalRef(env, jreq);

    nxt_unit_request_done(req, NXT_UNIT_OK);
}

