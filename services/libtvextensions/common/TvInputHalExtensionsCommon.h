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
#ifndef _TVINPUTHAL_EXTENSIONS_COMMON_H_
#define _TVINPUTHAL_EXTENSIONS_COMMON_H_

namespace android {

static const char * CUSTOMIZATION_LIB_NAME = "libTvInputHalEnhancements.so";

typedef void *(*createFunction_t)(void);

template <typename T>
struct ExtensionsLoader {

    static T *createInstance(const char *createFunctionName);

private:
    static void loadLib();
    static createFunction_t loadCreateFunction(const char *createFunctionName);
    static void *mLibHandle;
};

/*
 * Boiler-plate to declare the class as a singleton (with a static getter)
 * which can be loaded (dlopen'd) via ExtensionsLoader
 */
#define DECLARE_LOADABLE_SINGLETON(className)   \
protected:                                      \
    className();                                \
    virtual ~className();                       \
    static className *sInst;                    \
private:                                        \
    className(const className&);                \
    className &operator=(className &);          \
public:                                         \
    static className *get() {                   \
        return sInst;                           \
    }                                           \
    friend struct ExtensionsLoader<className>;

} //namespace android

#endif // _TVINPUTHAL_EXTENSIONS_COMMON_H_
