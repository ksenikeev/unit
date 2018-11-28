
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

#include "java/nxt_jni_Thread.h"
#include "java/nxt_jni_Context.h"
#include "java/nxt_jni_Request.h"
#include "java/nxt_jni_Response.h"
#include "java/nxt_jni_InputStream.h"
#include "java/nxt_jni_OutputStream.h"
#include "java/nxt_jni_URLClassLoader.h"

#include "nxt_java_jars.h"

static nxt_int_t nxt_java_pre_init(nxt_task_t *task,
    nxt_common_app_conf_t *conf);
static nxt_int_t nxt_java_init(nxt_task_t *task, nxt_common_app_conf_t *conf);
static void nxt_java_request_handler(nxt_unit_request_info_t *req);

static uint32_t  compat[] = {
    NXT_VERNUM, NXT_DEBUG,
};

char  *nxt_java_modules;


#define STR1(x)  #x
#define STR(x) STR1(x)

NXT_EXPORT nxt_app_module_t  nxt_app_module = {
    sizeof(compat),
    compat,
    nxt_string("java"),
    STR(NXT_JAVA_VERSION),
    nxt_java_pre_init,
    nxt_java_init,
};

typedef struct {
    JNIEnv   *env;
    jobject  ctx;
} nxt_java_data_t;


static nxt_int_t
nxt_java_pre_init(nxt_task_t *task, nxt_common_app_conf_t *conf)
{
    char       *modules, *slash;
    nxt_int_t  modules_len;

    modules = (char *) task->thread->runtime->modules;

    slash = strrchr(modules, '/');
    if (slash != NULL) {
        modules_len = slash - modules;

        modules = malloc(modules_len + 1);
        if (modules == NULL) {
            return NXT_ERROR;
        }

        memcpy(modules, task->thread->runtime->modules, modules_len);
        modules[modules_len] = '\0';

    } else {
        modules_len = nxt_strlen(modules);
    }

    nxt_java_modules = realpath(modules, NULL);

    if (slash != NULL) {
        free(modules);
    }

    return NXT_OK;
}


static nxt_int_t
nxt_java_init(nxt_task_t *task, nxt_common_app_conf_t *conf)
{
    jint                 rc;
    char                 *opt, **classpath_arr, **unit_jars;
    JavaVM               *jvm;
    JNIEnv               *env;
    nxt_str_t            str;
    nxt_int_t            opt_len, modules_len;
    nxt_uint_t           i, unit_jars_count, classpath_count;
    const char           **jar;
    JavaVMOption         *jvm_opt;
    JavaVMInitArgs       jvm_args;
    nxt_unit_ctx_t       *ctx;
    nxt_unit_init_t      java_init;
    nxt_java_data_t      data;
    nxt_conf_value_t     *value;
    nxt_java_app_conf_t  *c;

    //setenv("ASAN_OPTIONS", "handle_segv=0", 1);

    jvm_args.version = JNI_VERSION_1_6;
    jvm_args.nOptions = 0;
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

    modules_len = nxt_strlen(nxt_java_modules);

    unit_jars_count = sizeof(nxt_java_unit_jars) / sizeof(nxt_java_unit_jars[0])
                      - 1;

    unit_jars = malloc(unit_jars_count * sizeof(char*));
    if (unit_jars == NULL) {
        nxt_alert(task, "failed to allocate buffer for unit_jar array");

        return NXT_ERROR;
    }

    for (i = 0, jar = nxt_java_unit_jars; *jar != NULL; jar++) {
        opt_len = nxt_length("file:") + modules_len + nxt_length("/")
                   + nxt_strlen(*jar) + 1;
        opt = malloc(opt_len);
        if (opt == NULL) {
            nxt_alert(task, "failed to allocate buffer for unit jar");

            return NXT_ERROR;
        }

        unit_jars[i++] = opt;

        opt = nxt_cpymem(opt, "file:", nxt_length("file:"));
        opt = nxt_cpymem(opt, nxt_java_modules, modules_len);
        *opt++ = '/';
        opt = nxt_cpymem(opt, *jar, nxt_strlen(*jar));
        *opt++ = '\0';
    }

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

            jvm_opt[i].optionString = opt;
        }
    }

    if (c->classpath != NULL) {
        classpath_count = nxt_conf_array_count(c->classpath);
        classpath_arr = malloc(classpath_count * sizeof(char *));

        for (i = 0; /* void */ ; i++) {
            value = nxt_conf_get_array_element(c->classpath, i);
            if (value == NULL) {
                break;
            }

            nxt_conf_get_string(value, &str);

            opt_len = str.length + 1;

            char *sc = memchr(str.start, ':', str.length);
            if (sc == NULL) {
                opt_len += nxt_length("file:");
            }

            opt = malloc(opt_len);
            if (opt == NULL) {
                nxt_alert(task, "failed to allocate classpath");
                return NXT_ERROR;
            }

            classpath_arr[i] = opt;

            if (sc == NULL) {
                opt = nxt_cpymem(opt, "file:", nxt_length("file:"));
            }

            opt = nxt_cpymem(opt, str.start, str.length);
            *opt = '\0';
        }

    } else {
        classpath_count = 0;
        classpath_arr = NULL;
    }

    rc = JNI_CreateJavaVM(&jvm, (void **) &env, &jvm_args);
    if (rc != JNI_OK) {
        nxt_alert(task, "failed to create Java VM: %d", (int) rc);
        return NXT_ERROR;
    }

    rc = nxt_java_initThread(env);
    if (rc != NXT_UNIT_OK) {
        nxt_alert(task, "nxt_java_initThread() failed");
        goto env_failed;
    }

    rc = nxt_java_initURLClassLoader(env);
    if (rc != NXT_UNIT_OK) {
        nxt_alert(task, "nxt_java_initThread() failed");
        goto env_failed;
    }

    jobject cl = nxt_java_getContextClassLoader(env);
    if (cl == NULL) {
        nxt_alert(task, "nxt_java_getContextClassLoader failed");
        goto env_failed;
    }

    for (jar = nxt_java_system_jars; *jar != NULL; jar++) {
        opt_len = nxt_length("file:") + modules_len + nxt_length("/")
                   + nxt_strlen(*jar) + 1;
        opt = malloc(opt_len);
        if (opt == NULL) {
            nxt_alert(task, "failed to allocate buffer for system jar");

            return NXT_ERROR;
        }
        char *url = opt;

        opt = nxt_cpymem(opt, "file:", nxt_length("file:"));
        opt = nxt_cpymem(opt, nxt_java_modules, modules_len);
        *opt++ = '/';
        opt = nxt_cpymem(opt, *jar, nxt_strlen(*jar));
        *opt++ = '\0';

        nxt_java_addURL(env, cl, url);

        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionDescribe(env);
        }

        free(url);
    }

    cl = nxt_java_newURLClassLoader(env, unit_jars_count, unit_jars);
    if (cl == NULL) {
        nxt_alert(task, "nxt_java_newURLClassLoader failed");
        goto env_failed;
    }

    nxt_java_setContextClassLoader(env, cl);

    rc = nxt_java_initContext(env, cl);
    if (rc != NXT_UNIT_OK) {
        nxt_alert(task, "nxt_java_initContext() failed");
        goto env_failed;
    }

    rc = nxt_java_initRequest(env, cl);
    if (rc != NXT_UNIT_OK) {
        nxt_alert(task, "nxt_java_initRequest() failed");
        goto env_failed;
    }

    rc = nxt_java_initResponse(env, cl);
    if (rc != NXT_UNIT_OK) {
        nxt_alert(task, "nxt_java_initResponse() failed");
        goto env_failed;
    }

    rc = nxt_java_initInputStream(env, cl);
    if (rc != NXT_UNIT_OK) {
        nxt_alert(task, "nxt_java_initInputStream() failed");
        goto env_failed;
    }

    rc = nxt_java_initOutputStream(env, cl);
    if (rc != NXT_UNIT_OK) {
        nxt_alert(task, "nxt_java_initOutputStream() failed");
        goto env_failed;
    }

    nxt_java_jni_init(env);
    if (rc != NXT_UNIT_OK) {
        nxt_alert(task, "nxt_java_jni_init() failed");
        goto env_failed;
    }

    data.env = env;
    data.ctx = nxt_java_startContext(env, c->webapp, classpath_count,
                                     classpath_arr);

    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionDescribe(env);
    }

    nxt_unit_default_init(task, &java_init);

    java_init.callbacks.request_handler = nxt_java_request_handler;
    java_init.request_data_size = sizeof(nxt_java_request_data_t);
    java_init.data = &data;

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

    nxt_java_stopContext(env, data.ctx);

    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionDescribe(env);
    }

    (*jvm)->DestroyJavaVM(jvm);

    exit(0);

    return NXT_OK;

env_failed:

    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionDescribe(env);
    }

    return NXT_ERROR;
}


static void
nxt_java_request_handler(nxt_unit_request_info_t *req)
{
    nxt_java_data_t          *java_data = req->unit->data;
    JNIEnv                   *env = java_data->env;
    nxt_java_request_data_t  *data = req->data;

    jobject jreq = nxt_java_newRequest(env, java_data->ctx, req);

    if (jreq == NULL) {
        nxt_unit_req_alert(req, "failed to create Request instance");

        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);
        }

        nxt_unit_request_done(req, NXT_UNIT_ERROR);
        return;
    }

    jobject jresp = nxt_java_newResponse(env, req);

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
    data->jreq = jreq;
    data->jresp = jresp;
    data->buf = NULL;

    nxt_unit_request_group_dup_fields(req);

    nxt_java_service(env, java_data->ctx, jreq, jresp);

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

