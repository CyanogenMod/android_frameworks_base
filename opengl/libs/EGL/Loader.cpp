/*
 ** Copyright 2007, The Android Open Source Project
 **
 ** Licensed under the Apache License, Version 2.0 (the "License");
 ** you may not use this file except in compliance with the License.
 ** You may obtain a copy of the License at
 **
 **     http://www.apache.org/licenses/LICENSE-2.0
 **
 ** Unless required by applicable law or agreed to in writing, software
 ** distributed under the License is distributed on an "AS IS" BASIS,
 ** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ** See the License for the specific language governing permissions and
 ** limitations under the License.
 */

#include <ctype.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <dlfcn.h>
#include <limits.h>

#include <cutils/log.h>

#include <EGL/egl.h>

#include "hooks.h"
#include "egl_impl.h"

#include "Loader.h"

// ----------------------------------------------------------------------------
namespace android {
// ----------------------------------------------------------------------------


/*
 * EGL drivers are called
 *
 * /system/lib/egl/lib{[EGL|GLESv1_CM|GLESv2] | GLES}_$TAG.so
 *
 */

ANDROID_SINGLETON_STATIC_INSTANCE( Loader )

// ----------------------------------------------------------------------------

Loader::driver_t::driver_t(void* gles)
{
    dso[0] = gles;
    for (size_t i=1 ; i<NELEM(dso) ; i++)
        dso[i] = 0;
}

Loader::driver_t::~driver_t()
{
    for (size_t i=0 ; i<NELEM(dso) ; i++) {
        if (dso[i]) {
            dlclose(dso[i]);
            dso[i] = 0;
        }
    }
}

status_t Loader::driver_t::set(void* hnd, int32_t api)
{
    switch (api) {
        case EGL:
            dso[0] = hnd;
            break;
        case GLESv1_CM:
            dso[1] = hnd;
            break;
        case GLESv2:
            dso[2] = hnd;
            break;
        default:
            return BAD_INDEX;
    }
    return NO_ERROR;
}

// ----------------------------------------------------------------------------

Loader::entry_t::entry_t(int dpy, int impl, const char* tag)
    : dpy(dpy), impl(impl), tag(tag) {
}

// ----------------------------------------------------------------------------

Loader::Loader()
{
    char line[256];
    char tag[256];
    FILE* cfg = fopen("/system/lib/egl/egl.cfg", "r");
    if (cfg == NULL) {
        // default config
        LOGD("egl.cfg not found, using default config");
        gConfig.add( entry_t(0, 0, "android") );
    } else {
        while (fgets(line, 256, cfg)) {
            int dpy;
            int impl;
            if (sscanf(line, "%u %u %s", &dpy, &impl, tag) == 3) {
                //LOGD(">>> %u %u %s", dpy, impl, tag);
                gConfig.add( entry_t(dpy, impl, tag) );
            }
        }
        fclose(cfg);
    }
}

Loader::~Loader()
{
}

const char* Loader::getTag(int dpy, int impl)
{
    const Vector<entry_t>& cfgs(gConfig);
    const size_t c = cfgs.size();
    for (size_t i=0 ; i<c ; i++) {
        if (dpy == cfgs[i].dpy)
            if (impl == cfgs[i].impl)
                return cfgs[i].tag.string();
    }
    return 0;
}

void* Loader::open(EGLNativeDisplayType display, int impl, egl_connection_t* cnx)
{
    /*
     * TODO: if we don't find display/0, then use 0/0
     * (0/0 should always work)
     */

    void* dso;
    int index = int(display);
    driver_t* hnd = 0;

    char const* tag = getTag(index, impl);
    if (tag) {
        dso = load_driver("GLES", tag, cnx, EGL | GLESv1_CM | GLESv2);
        if (dso) {
            hnd = new driver_t(dso);
        } else {
            // Always load EGL first
            dso = load_driver("EGL", tag, cnx, EGL);
            if (dso) {
                hnd = new driver_t(dso);

                // TODO: make this more automated
                hnd->set( load_driver("GLESv1_CM", tag, cnx, GLESv1_CM), GLESv1_CM );

                hnd->set( load_driver("GLESv2", tag, cnx, GLESv2), GLESv2 );
            }
        }
    }

    LOG_FATAL_IF(!index && !impl && !hnd,
            "couldn't find the default OpenGL ES implementation "
            "for default display");

    return (void*)hnd;
}

status_t Loader::close(void* driver)
{
    driver_t* hnd = (driver_t*)driver;
    delete hnd;
    return NO_ERROR;
}

void Loader::init_api(void* dso,
        char const * const * api,
        __eglMustCastToProperFunctionPointerType* curr,
        getProcAddressType getProcAddress)
{
    char scrap[256];
    while (*api) {
        char const * name = *api;
        __eglMustCastToProperFunctionPointerType f =
            (__eglMustCastToProperFunctionPointerType)dlsym(dso, name);
        if (f == NULL) {
            // couldn't find the entry-point, use eglGetProcAddress()
            f = getProcAddress(name);
        }
        if (f == NULL) {
            // Try without the OES postfix
            ssize_t index = ssize_t(strlen(name)) - 3;
            if ((index>0 && (index<255)) && (!strcmp(name+index, "OES"))) {
                strncpy(scrap, name, index);
                scrap[index] = 0;
                f = (__eglMustCastToProperFunctionPointerType)dlsym(dso, scrap);
                //LOGD_IF(f, "found <%s> instead", scrap);
            }
        }
        if (f == NULL) {
            // Try with the OES postfix
            ssize_t index = ssize_t(strlen(name)) - 3;
            if ((index>0 && (index<252)) && (strcmp(name+index, "OES"))) {
                strncpy(scrap, name, index);
                scrap[index] = 0;
                strcat(scrap, "OES");
                f = (__eglMustCastToProperFunctionPointerType)dlsym(dso, scrap);
                //LOGD_IF(f, "found <%s> instead", scrap);
            }
        }
        if (f == NULL) {
            //LOGD("%s", name);
            f = (__eglMustCastToProperFunctionPointerType)gl_unimplemented;
        }
        *curr++ = f;
        api++;
    }
}

void *Loader::load_driver(const char* kind, const char *tag,
        egl_connection_t* cnx, uint32_t mask)
{
    char driver_absolute_path[PATH_MAX];
    const char* const search1 = "/vendor/lib/egl/lib%s_%s.so";
    const char* const search2 = "/system/lib/egl/lib%s_%s.so";

    snprintf(driver_absolute_path, PATH_MAX, search1, kind, tag);
    if (access(driver_absolute_path, R_OK)) {
        snprintf(driver_absolute_path, PATH_MAX, search2, kind, tag);
        if (access(driver_absolute_path, R_OK)) {
            // this happens often, we don't want to log an error
            return 0;
        }
    }

    void* dso = dlopen(driver_absolute_path, RTLD_NOW | RTLD_LOCAL);
    if (dso == 0) {
        const char* err = dlerror();
        LOGE("load_driver(%s): %s", driver_absolute_path, err?err:"unknown");
        return 0;
    }

    LOGD("loaded %s", driver_absolute_path);

    if (mask & EGL) {
        getProcAddress = (getProcAddressType)dlsym(dso, "eglGetProcAddress");

        LOGE_IF(!getProcAddress,
                "can't find eglGetProcAddress() in %s", driver_absolute_path);

        egl_t* egl = &cnx->egl;
        __eglMustCastToProperFunctionPointerType* curr =
            (__eglMustCastToProperFunctionPointerType*)egl;
        char const * const * api = egl_names;
        while (*api) {
            char const * name = *api;
            __eglMustCastToProperFunctionPointerType f =
                (__eglMustCastToProperFunctionPointerType)dlsym(dso, name);
            if (f == NULL) {
                // couldn't find the entry-point, use eglGetProcAddress()
                f = getProcAddress(name);
                if (f == NULL) {
                    f = (__eglMustCastToProperFunctionPointerType)0;
                }
            }
            *curr++ = f;
            api++;
        }
    }

    if (mask & GLESv1_CM) {
        init_api(dso, gl_names,
            (__eglMustCastToProperFunctionPointerType*)
                &cnx->hooks[GLESv1_INDEX]->gl,
            getProcAddress);
    }

    if (mask & GLESv2) {
      init_api(dso, gl_names,
            (__eglMustCastToProperFunctionPointerType*)
                &cnx->hooks[GLESv2_INDEX]->gl,
            getProcAddress);
    }

    return dso;
}

// ----------------------------------------------------------------------------
}; // namespace android
// ----------------------------------------------------------------------------
