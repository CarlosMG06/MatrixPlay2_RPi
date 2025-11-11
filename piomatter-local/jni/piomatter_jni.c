#include <jni.h>
#include <stdint.h>
#include "piomatter_abi.h"

// Cache d'excepció RuntimeException
static jclass g_RuntimeEx = NULL;

static void throw_re(JNIEnv* env, const char* msg) {
    if (!g_RuntimeEx) {
        jclass tmp = (*env)->FindClass(env, "java/lang/RuntimeException");
        g_RuntimeEx = (jclass)(*env)->NewGlobalRef(env, tmp);
    }
    (*env)->ThrowNew(env, g_RuntimeEx, msg);
}

JNIEXPORT jlong JNICALL
Java_com_piomatter_PioMatter_nativeOpen
  (JNIEnv* env, jclass cls, jint w, jint h, jint addrLines, jint lanes, jint brightness, jint fpsCap)
{
    (void)cls;
    pm_config cfg = {
        .width = w,
        .height = h,
        .n_addr_lines = addrLines,
        .lanes = lanes,
        .brightness_0_255 = brightness,
        .fps_cap = fpsCap
    };
    pm_device* d = pm_open(&cfg);
    if (!d) { throw_re(env, "pm_open() failed"); return 0; }
    return (jlong)(uintptr_t)d;
}

JNIEXPORT void JNICALL
Java_com_piomatter_PioMatter_nativeClose
  (JNIEnv* env, jclass cls, jlong handle)
{
    (void)env; (void)cls;
    pm_device* d = (pm_device*)(uintptr_t)handle;
    pm_close(d);
}

JNIEXPORT jint JNICALL
Java_com_piomatter_PioMatter_nativeSetBrightness
  (JNIEnv* env, jclass cls, jlong handle, jint value)
{
    (void)env; (void)cls;
    pm_device* d = (pm_device*)(uintptr_t)handle;
    return pm_set_brightness(d, value);
}

JNIEXPORT jint JNICALL
Java_com_piomatter_PioMatter_nativeSwap
  (JNIEnv* env, jclass cls, jlong handle)
{
    (void)env; (void)cls;
    pm_device* d = (pm_device*)(uintptr_t)handle;
    return pm_swap_buffers(d);
}

JNIEXPORT jobject JNICALL
Java_com_piomatter_PioMatter_nativeMapFramebuffer
  (JNIEnv* env, jclass cls, jlong handle)
{
    (void)cls;
    pm_device* d = (pm_device*)(uintptr_t)handle;
    int w=0, h=0, stride=0, bpp=0;
    uint8_t* fb = pm_map_framebuffer(d, &w, &h, &stride, &bpp);
    if (!fb) { throw_re(env, "pm_map_framebuffer() returned NULL"); return NULL; }

    // ByteBuffer directe sense còpia
    jobject byteBuf = (*env)->NewDirectByteBuffer(env, fb, (jlong) (h * stride));
    if (!byteBuf) { throw_re(env, "NewDirectByteBuffer failed"); return NULL; }

    // Construeix l'objecte com.piomatter.PioMatter$FB(ByteBuffer,int,int,int,int)
    jclass fbCls = (*env)->FindClass(env, "com/piomatter/PioMatter$FB");
    if (!fbCls) { throw_re(env, "Can't find PioMatter$FB"); return NULL; }
    jmethodID ctor = (*env)->GetMethodID(env, fbCls, "<init>", "(Ljava/nio/ByteBuffer;IIII)V");
    if (!ctor) { throw_re(env, "Can't find FB.<init>(ByteBuffer,int,int,int,int)"); return NULL; }

    return (*env)->NewObject(env, fbCls, ctor, byteBuf, w, h, stride, bpp);
}

JNIEXPORT jint JNICALL
Java_com_piomatter_PioMatter_nativePutPixel
  (JNIEnv* env, jclass cls, jlong handle, jint x, jint y, jint r, jint g, jint b)
{
    (void)env; (void)cls;
    pm_device* d = (pm_device*)(uintptr_t)handle;
    return pm_put_pixel(d, x, y, (uint8_t)r, (uint8_t)g, (uint8_t)b);
}
