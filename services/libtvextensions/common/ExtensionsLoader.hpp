/*
 * Copyright (c) 2013 - 2015, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
#include <dlfcn.h>
#include <common/TvInputHalExtensionsCommon.h>

namespace android {

/*
 * Create strongly-typed objects of type T
 * If the customization library exists and does contain a "named" constructor,
 *  invoke and create an instance
 * Else create the object of type T itself
 *
 * Contains a static instance to dlopen'd library, But may end up
 * opening the library mutiple times. Following snip from dlopen man page is
 * reassuring "...Only a single copy of an object file is brought into the
 * address space, even if dlopen() is invoked multiple times in reference to
 * the file, and even if different pathnames are used to reference the file.."
 */

template <typename T>
T *ExtensionsLoader<T>::createInstance(const char *createFunctionName) {
        ALOGV("createInstance(%dbit) : %s", sizeof(intptr_t)*8, createFunctionName);
        // create extended object if extensions-lib is available and
        // TVINPUT_HAL_EXTENSIONS is enabled
#if ENABLE_TVINPUT_HAL_EXTENSIONS
        createFunction_t createFunc = loadCreateFunction(createFunctionName);
        if (createFunc) {
            return reinterpret_cast<T *>((*createFunc)());
        }
#endif
        // Else, create the default object
        return new T;
}

template <typename T>
void ExtensionsLoader<T>::loadLib() {
        if (!mLibHandle) {
            mLibHandle = ::dlopen(CUSTOMIZATION_LIB_NAME, RTLD_LAZY);
            if (!mLibHandle) {
                ALOGV("%s", dlerror());
                return;
            }
            ALOGV("Opened %s", CUSTOMIZATION_LIB_NAME);
        }
}

template <typename T>
createFunction_t ExtensionsLoader<T>::loadCreateFunction(const char *createFunctionName) {
        loadLib();
        if (!mLibHandle) {
            return NULL;
        }
        createFunction_t func = (createFunction_t)dlsym(mLibHandle, createFunctionName);
        if (!func) {
            ALOGW("symbol %s not found:  %s",createFunctionName, dlerror());
        }
        return func;
}

//static
template <typename T>
void *ExtensionsLoader<T>::mLibHandle = NULL;

} //namespace android
