#include "SkTypes.h"
#include "SkImageDecoder.h"

#define LOG_TAG "EmojiFactory_jni"
#include <utils/Log.h>
#include <utils/String8.h>
#include <utils/String16.h>

#include "EmojiFactory.h"
#include <nativehelper/JNIHelp.h>

#include <dlfcn.h>
// #include <pthread.h>

namespace android {

class EmojiFactoryCaller {
 public:
  EmojiFactoryCaller() {}
  virtual ~EmojiFactoryCaller();
  bool Init();
  EmojiFactory *TryCallGetImplementation(const char* name);
  EmojiFactory *TryCallGetAvailableImplementation();
 private:
  void *m_handle;
  EmojiFactory *(*m_get_implementation)(const char*);
  EmojiFactory *(*m_get_available_implementation)();
};

bool EmojiFactoryCaller::Init() {
  const char* error_msg;
  m_handle = dlopen("libemoji.so", RTLD_LAZY | RTLD_LOCAL);

  if (m_handle == NULL) {
    error_msg = "Failed to load libemoji.so";
    goto FAIL;
  }

  m_get_implementation =
      reinterpret_cast<EmojiFactory *(*)(const char*)>(
          dlsym(m_handle, "GetImplementation"));
  if (m_get_implementation == NULL) {
    error_msg = "Failed to get symbol of GetImplementation";
    goto FAIL;
  }

  m_get_available_implementation =
      reinterpret_cast<EmojiFactory *(*)()>(
          dlsym(m_handle,"GetAvailableImplementation"));
  if (m_get_available_implementation == NULL) {
    error_msg = "Failed to get symbol of GetAvailableImplementation";
    goto FAIL;
  }

  return true;

FAIL:
  const char* error_str = dlerror();
  if (error_str == NULL) {
    error_str = "unknown reason";
  }

  ALOGE("%s: %s", error_msg, error_str);
  if (m_handle != NULL) {
    dlclose(m_handle);
    m_handle = NULL;
  }
  return false;
}

EmojiFactoryCaller::~EmojiFactoryCaller() {
  if (m_handle) {
    dlclose(m_handle);
  }
}

EmojiFactory *EmojiFactoryCaller::TryCallGetImplementation(
    const char* name) {
  if (NULL == m_handle) {
    return NULL;
  }
  return m_get_implementation(name);
}

EmojiFactory *EmojiFactoryCaller::TryCallGetAvailableImplementation() {
  if (NULL == m_handle) {
    return NULL;
  }
  return m_get_available_implementation();
}

static EmojiFactoryCaller* gCaller;
static pthread_once_t g_once = PTHREAD_ONCE_INIT;
static bool lib_emoji_factory_is_ready;

static jclass    gBitmap_class;
static jmethodID gBitmap_constructorMethodID;

static jclass    gEmojiFactory_class;
static jmethodID gEmojiFactory_constructorMethodID;

static void InitializeCaller() {
  gCaller = new EmojiFactoryCaller();
  lib_emoji_factory_is_ready = gCaller->Init();
}

static jobject create_java_EmojiFactory(
    JNIEnv* env, EmojiFactory* factory, jstring name) {
  jobject obj = env->NewObject(gEmojiFactory_class, gEmojiFactory_constructorMethodID,
      static_cast<jint>(reinterpret_cast<uintptr_t>(factory)), name);
  if (env->ExceptionCheck() != 0) {
    ALOGE("*** Uncaught exception returned from Java call!\n");
    env->ExceptionDescribe();
  }
  return obj;
}

static jobject android_emoji_EmojiFactory_newInstance(
    JNIEnv* env, jobject clazz, jstring name) {
  if (NULL == name) {
    return NULL;
  }
  pthread_once(&g_once, InitializeCaller);
  if (!lib_emoji_factory_is_ready) {
    return NULL;
  }

  const jchar* jchars = (const jchar*)env->GetStringChars(name, NULL);
  jsize len = env->GetStringLength(name);
  String8 str(String16((const char16_t*)jchars, len));

  EmojiFactory *factory = gCaller->TryCallGetImplementation(str.string());
  // EmojiFactory *factory = EmojiFactory::GetImplementation(str.string());
  if (NULL == factory) {
    return NULL;
  }
  env->ReleaseStringChars(name, jchars);

  return create_java_EmojiFactory(env, factory, name);
}

static jobject android_emoji_EmojiFactory_newAvailableInstance(
    JNIEnv* env, jobject clazz) {
  pthread_once(&g_once, InitializeCaller);
  if (!lib_emoji_factory_is_ready) {
    return NULL;
  }

  EmojiFactory *factory = gCaller->TryCallGetAvailableImplementation();
  // EmojiFactory *factory = EmojiFactory::GetAvailableImplementation();
  if (NULL == factory) {
    return NULL;
  }
  String16 name_16(String8(factory->Name()));
  jstring jname = env->NewString((const jchar*)name_16.string(), name_16.size());
  if (NULL == jname) {
    return NULL;
  }

  return create_java_EmojiFactory(env, factory, jname);
}

static jobject android_emoji_EmojiFactory_getBitmapFromAndroidPua(
    JNIEnv* env, jobject clazz, jint nativeEmojiFactory, jint pua) {
  EmojiFactory *factory = reinterpret_cast<EmojiFactory *>(nativeEmojiFactory);

  int size;
  const char *bytes = factory->GetImageBinaryFromAndroidPua(pua, &size);
  if (bytes == NULL) {
    return NULL;
  }

  SkBitmap *bitmap = new SkBitmap;
  if (!SkImageDecoder::DecodeMemory(bytes, size, bitmap)) {
    ALOGE("SkImageDecoder::DecodeMemory() failed.");
    return NULL;
  }

  jobject obj = env->NewObject(gBitmap_class, gBitmap_constructorMethodID,
      static_cast<jint>(reinterpret_cast<uintptr_t>(bitmap)), NULL, false, NULL, -1);
  if (env->ExceptionCheck() != 0) {
    ALOGE("*** Uncaught exception returned from Java call!\n");
    env->ExceptionDescribe();
  }
  return obj;
}

static void android_emoji_EmojiFactory_destructor(
    JNIEnv* env, jobject obj, jint nativeEmojiFactory) {
  /*
  // Must not delete this object!!
  EmojiFactory *factory = reinterpret_cast<EmojiFactory *>(nativeEmojiFactory);
  delete factory;
  */
}

static jint android_emoji_EmojiFactory_getAndroidPuaFromVendorSpecificSjis(
    JNIEnv* env, jobject obj, jint nativeEmojiFactory, jchar sjis) {
  EmojiFactory *factory = reinterpret_cast<EmojiFactory *>(nativeEmojiFactory);
  return factory->GetAndroidPuaFromVendorSpecificSjis(sjis);
}

static jint android_emoji_EmojiFactory_getVendorSpecificSjisFromAndroidPua(
    JNIEnv* env, jobject obj, jint nativeEmojiFactory, jint pua) {
  EmojiFactory *factory = reinterpret_cast<EmojiFactory *>(nativeEmojiFactory);
  return factory->GetVendorSpecificSjisFromAndroidPua(pua);
}

static jint android_emoji_EmojiFactory_getAndroidPuaFromVendorSpecificPua(
    JNIEnv* env, jobject obj, jint nativeEmojiFactory, jint vsu) {
  EmojiFactory *factory = reinterpret_cast<EmojiFactory *>(nativeEmojiFactory);
  return factory->GetAndroidPuaFromVendorSpecificPua(vsu);
}

static jint android_emoji_EmojiFactory_getVendorSpecificPuaFromAndroidPua(
    JNIEnv* env, jobject obj, jint nativeEmojiFactory, jint pua) {
  EmojiFactory *factory = reinterpret_cast<EmojiFactory *>(nativeEmojiFactory);
  return factory->GetVendorSpecificPuaFromAndroidPua(pua);
}

static jint android_emoji_EmojiFactory_getMaximumVendorSpecificPua(
    JNIEnv* env, jobject obj, jint nativeEmojiFactory) {
  EmojiFactory *factory = reinterpret_cast<EmojiFactory *>(nativeEmojiFactory);
  return factory->GetMaximumVendorSpecificPua();
}

static jint android_emoji_EmojiFactory_getMinimumVendorSpecificPua(
    JNIEnv* env, jobject obj, jint nativeEmojiFactory) {
  EmojiFactory *factory = reinterpret_cast<EmojiFactory *>(nativeEmojiFactory);
  return factory->GetMinimumVendorSpecificPua();
}

static jint android_emoji_EmojiFactory_getMaximumAndroidPua(
    JNIEnv* env, jobject obj, jint nativeEmojiFactory) {
  EmojiFactory *factory = reinterpret_cast<EmojiFactory *>(nativeEmojiFactory);
  return factory->GetMaximumAndroidPua();
}

static jint android_emoji_EmojiFactory_getMinimumAndroidPua(
    JNIEnv* env, jobject obj, jint nativeEmojiFactory) {
  EmojiFactory *factory = reinterpret_cast<EmojiFactory *>(nativeEmojiFactory);
  return factory->GetMinimumAndroidPua();
}

static JNINativeMethod gMethods[] = {
  { "newInstance", "(Ljava/lang/String;)Landroid/emoji/EmojiFactory;",
    (void*)android_emoji_EmojiFactory_newInstance},
  { "newAvailableInstance", "()Landroid/emoji/EmojiFactory;",
    (void*)android_emoji_EmojiFactory_newAvailableInstance},
  { "nativeDestructor", "(I)V",
    (void*)android_emoji_EmojiFactory_destructor},
  { "nativeGetBitmapFromAndroidPua", "(II)Landroid/graphics/Bitmap;",
    (void*)android_emoji_EmojiFactory_getBitmapFromAndroidPua},
  { "nativeGetAndroidPuaFromVendorSpecificSjis", "(IC)I",
    (void*)android_emoji_EmojiFactory_getAndroidPuaFromVendorSpecificSjis},
  { "nativeGetVendorSpecificSjisFromAndroidPua", "(II)I",
    (void*)android_emoji_EmojiFactory_getVendorSpecificSjisFromAndroidPua},
  { "nativeGetAndroidPuaFromVendorSpecificPua", "(II)I",
    (void*)android_emoji_EmojiFactory_getAndroidPuaFromVendorSpecificPua},
  { "nativeGetVendorSpecificPuaFromAndroidPua", "(II)I",
    (void*)android_emoji_EmojiFactory_getVendorSpecificPuaFromAndroidPua},
  { "nativeGetMaximumVendorSpecificPua", "(I)I",
    (void*)android_emoji_EmojiFactory_getMaximumVendorSpecificPua},
  { "nativeGetMinimumVendorSpecificPua", "(I)I",
    (void*)android_emoji_EmojiFactory_getMinimumVendorSpecificPua},
  { "nativeGetMaximumAndroidPua", "(I)I",
    (void*)android_emoji_EmojiFactory_getMaximumAndroidPua},
  { "nativeGetMinimumAndroidPua", "(I)I",
    (void*)android_emoji_EmojiFactory_getMinimumAndroidPua}
};

static jclass make_globalref(JNIEnv* env, const char classname[])
{
    jclass c = env->FindClass(classname);
    SkASSERT(c);
    return (jclass)env->NewGlobalRef(c);
}

static jfieldID getFieldIDCheck(JNIEnv* env, jclass clazz,
                                const char fieldname[], const char type[])
{
    jfieldID id = env->GetFieldID(clazz, fieldname, type);
    SkASSERT(id);
    return id;
}

int register_android_emoji_EmojiFactory(JNIEnv* env) {
  gBitmap_class = make_globalref(env, "android/graphics/Bitmap");
  gBitmap_constructorMethodID = env->GetMethodID(gBitmap_class, "<init>",
                                                 "(I[BZ[BI)V");
  gEmojiFactory_class = make_globalref(env, "android/emoji/EmojiFactory");
  gEmojiFactory_constructorMethodID = env->GetMethodID(
      gEmojiFactory_class, "<init>", "(ILjava/lang/String;)V");
  return jniRegisterNativeMethods(env, "android/emoji/EmojiFactory",
                                  gMethods, NELEM(gMethods));
}

}  // namespace android
