/* //device/java/android/javax/microedition/khronos/opengles/GL10.java
**
** Copyright 2006, The Android Open Source Project
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

// This source file is automatically generated

package javax.microedition.khronos.opengles;

public interface GL10 extends GL {
    int GL_ADD                                   = 0x0104;
    int GL_ALIASED_LINE_WIDTH_RANGE              = 0x846E;
    int GL_ALIASED_POINT_SIZE_RANGE              = 0x846D;
    int GL_ALPHA                                 = 0x1906;
    int GL_ALPHA_BITS                            = 0x0D55;
    int GL_ALPHA_TEST                            = 0x0BC0;
    int GL_ALWAYS                                = 0x0207;
    int GL_AMBIENT                               = 0x1200;
    int GL_AMBIENT_AND_DIFFUSE                   = 0x1602;
    int GL_AND                                   = 0x1501;
    int GL_AND_INVERTED                          = 0x1504;
    int GL_AND_REVERSE                           = 0x1502;
    int GL_BACK                                  = 0x0405;
    int GL_BLEND                                 = 0x0BE2;
    int GL_BLUE_BITS                             = 0x0D54;
    int GL_BYTE                                  = 0x1400;
    int GL_CCW                                   = 0x0901;
    int GL_CLAMP_TO_EDGE                         = 0x812F;
    int GL_CLEAR                                 = 0x1500;
    int GL_COLOR_ARRAY                           = 0x8076;
    int GL_COLOR_BUFFER_BIT                      = 0x4000;
    int GL_COLOR_LOGIC_OP                        = 0x0BF2;
    int GL_COLOR_MATERIAL                        = 0x0B57;
    int GL_COMPRESSED_TEXTURE_FORMATS            = 0x86A3;
    int GL_CONSTANT_ATTENUATION                  = 0x1207;
    int GL_COPY                                  = 0x1503;
    int GL_COPY_INVERTED                         = 0x150C;
    int GL_CULL_FACE                             = 0x0B44;
    int GL_CW                                    = 0x0900;
    int GL_DECAL                                 = 0x2101;
    int GL_DECR                                  = 0x1E03;
    int GL_DEPTH_BITS                            = 0x0D56;
    int GL_DEPTH_BUFFER_BIT                      = 0x0100;
    int GL_DEPTH_TEST                            = 0x0B71;
    int GL_DIFFUSE                               = 0x1201;
    int GL_DITHER                                = 0x0BD0;
    int GL_DONT_CARE                             = 0x1100;
    int GL_DST_ALPHA                             = 0x0304;
    int GL_DST_COLOR                             = 0x0306;
    int GL_EMISSION                              = 0x1600;
    int GL_EQUAL                                 = 0x0202;
    int GL_EQUIV                                 = 0x1509;
    int GL_EXP                                   = 0x0800;
    int GL_EXP2                                  = 0x0801;
    int GL_EXTENSIONS                            = 0x1F03;
    int GL_FALSE                                 = 0;
    int GL_FASTEST                               = 0x1101;
    int GL_FIXED                                 = 0x140C;
    int GL_FLAT                                  = 0x1D00;
    int GL_FLOAT                                 = 0x1406;
    int GL_FOG                                   = 0x0B60;
    int GL_FOG_COLOR                             = 0x0B66;
    int GL_FOG_DENSITY                           = 0x0B62;
    int GL_FOG_END                               = 0x0B64;
    int GL_FOG_HINT                              = 0x0C54;
    int GL_FOG_MODE                              = 0x0B65;
    int GL_FOG_START                             = 0x0B63;
    int GL_FRONT                                 = 0x0404;
    int GL_FRONT_AND_BACK                        = 0x0408;
    int GL_GEQUAL                                = 0x0206;
    int GL_GREATER                               = 0x0204;
    int GL_GREEN_BITS                            = 0x0D53;
    int GL_IMPLEMENTATION_COLOR_READ_FORMAT_OES  = 0x8B9B;
    int GL_IMPLEMENTATION_COLOR_READ_TYPE_OES    = 0x8B9A;
    int GL_INCR                                  = 0x1E02;
    int GL_INVALID_ENUM                          = 0x0500;
    int GL_INVALID_OPERATION                     = 0x0502;
    int GL_INVALID_VALUE                         = 0x0501;
    int GL_INVERT                                = 0x150A;
    int GL_KEEP                                  = 0x1E00;
    int GL_LEQUAL                                = 0x0203;
    int GL_LESS                                  = 0x0201;
    int GL_LIGHT_MODEL_AMBIENT                   = 0x0B53;
    int GL_LIGHT_MODEL_TWO_SIDE                  = 0x0B52;
    int GL_LIGHT0                                = 0x4000;
    int GL_LIGHT1                                = 0x4001;
    int GL_LIGHT2                                = 0x4002;
    int GL_LIGHT3                                = 0x4003;
    int GL_LIGHT4                                = 0x4004;
    int GL_LIGHT5                                = 0x4005;
    int GL_LIGHT6                                = 0x4006;
    int GL_LIGHT7                                = 0x4007;
    int GL_LIGHTING                              = 0x0B50;
    int GL_LINE_LOOP                             = 0x0002;
    int GL_LINE_SMOOTH                           = 0x0B20;
    int GL_LINE_SMOOTH_HINT                      = 0x0C52;
    int GL_LINE_STRIP                            = 0x0003;
    int GL_LINEAR                                = 0x2601;
    int GL_LINEAR_ATTENUATION                    = 0x1208;
    int GL_LINEAR_MIPMAP_LINEAR                  = 0x2703;
    int GL_LINEAR_MIPMAP_NEAREST                 = 0x2701;
    int GL_LINES                                 = 0x0001;
    int GL_LUMINANCE                             = 0x1909;
    int GL_LUMINANCE_ALPHA                       = 0x190A;
    int GL_MAX_ELEMENTS_INDICES                  = 0x80E9;
    int GL_MAX_ELEMENTS_VERTICES                 = 0x80E8;
    int GL_MAX_LIGHTS                            = 0x0D31;
    int GL_MAX_MODELVIEW_STACK_DEPTH             = 0x0D36;
    int GL_MAX_PROJECTION_STACK_DEPTH            = 0x0D38;
    int GL_MAX_TEXTURE_SIZE                      = 0x0D33;
    int GL_MAX_TEXTURE_STACK_DEPTH               = 0x0D39;
    int GL_MAX_TEXTURE_UNITS                     = 0x84E2;
    int GL_MAX_VIEWPORT_DIMS                     = 0x0D3A;
    int GL_MODELVIEW                             = 0x1700;
    int GL_MODULATE                              = 0x2100;
    int GL_MULTISAMPLE                           = 0x809D;
    int GL_NAND                                  = 0x150E;
    int GL_NEAREST                               = 0x2600;
    int GL_NEAREST_MIPMAP_LINEAR                 = 0x2702;
    int GL_NEAREST_MIPMAP_NEAREST                = 0x2700;
    int GL_NEVER                                 = 0x0200;
    int GL_NICEST                                = 0x1102;
    int GL_NO_ERROR                              = 0;
    int GL_NOOP                                  = 0x1505;
    int GL_NOR                                   = 0x1508;
    int GL_NORMAL_ARRAY                          = 0x8075;
    int GL_NORMALIZE                             = 0x0BA1;
    int GL_NOTEQUAL                              = 0x0205;
    int GL_NUM_COMPRESSED_TEXTURE_FORMATS        = 0x86A2;
    int GL_ONE                                   = 1;
    int GL_ONE_MINUS_DST_ALPHA                   = 0x0305;
    int GL_ONE_MINUS_DST_COLOR                   = 0x0307;
    int GL_ONE_MINUS_SRC_ALPHA                   = 0x0303;
    int GL_ONE_MINUS_SRC_COLOR                   = 0x0301;
    int GL_OR                                    = 0x1507;
    int GL_OR_INVERTED                           = 0x150D;
    int GL_OR_REVERSE                            = 0x150B;
    int GL_OUT_OF_MEMORY                         = 0x0505;
    int GL_PACK_ALIGNMENT                        = 0x0D05;
    int GL_PALETTE4_R5_G6_B5_OES                 = 0x8B92;
    int GL_PALETTE4_RGB5_A1_OES                  = 0x8B94;
    int GL_PALETTE4_RGB8_OES                     = 0x8B90;
    int GL_PALETTE4_RGBA4_OES                    = 0x8B93;
    int GL_PALETTE4_RGBA8_OES                    = 0x8B91;
    int GL_PALETTE8_R5_G6_B5_OES                 = 0x8B97;
    int GL_PALETTE8_RGB5_A1_OES                  = 0x8B99;
    int GL_PALETTE8_RGB8_OES                     = 0x8B95;
    int GL_PALETTE8_RGBA4_OES                    = 0x8B98;
    int GL_PALETTE8_RGBA8_OES                    = 0x8B96;
    int GL_PERSPECTIVE_CORRECTION_HINT           = 0x0C50;
    int GL_POINT_SMOOTH                          = 0x0B10;
    int GL_POINT_SMOOTH_HINT                     = 0x0C51;
    int GL_POINTS                                = 0x0000;
    int GL_POINT_FADE_THRESHOLD_SIZE             = 0x8128;
    int GL_POINT_SIZE                            = 0x0B11;
    int GL_POLYGON_OFFSET_FILL                   = 0x8037;
    int GL_POLYGON_SMOOTH_HINT                   = 0x0C53;
    int GL_POSITION                              = 0x1203;
    int GL_PROJECTION                            = 0x1701;
    int GL_QUADRATIC_ATTENUATION                 = 0x1209;
    int GL_RED_BITS                              = 0x0D52;
    int GL_RENDERER                              = 0x1F01;
    int GL_REPEAT                                = 0x2901;
    int GL_REPLACE                               = 0x1E01;
    int GL_RESCALE_NORMAL                        = 0x803A;
    int GL_RGB                                   = 0x1907;
    int GL_RGBA                                  = 0x1908;
    int GL_SAMPLE_ALPHA_TO_COVERAGE              = 0x809E;
    int GL_SAMPLE_ALPHA_TO_ONE                   = 0x809F;
    int GL_SAMPLE_COVERAGE                       = 0x80A0;
    int GL_SCISSOR_TEST                          = 0x0C11;
    int GL_SET                                   = 0x150F;
    int GL_SHININESS                             = 0x1601;
    int GL_SHORT                                 = 0x1402;
    int GL_SMOOTH                                = 0x1D01;
    int GL_SMOOTH_LINE_WIDTH_RANGE               = 0x0B22;
    int GL_SMOOTH_POINT_SIZE_RANGE               = 0x0B12;
    int GL_SPECULAR                              = 0x1202;
    int GL_SPOT_CUTOFF                           = 0x1206;
    int GL_SPOT_DIRECTION                        = 0x1204;
    int GL_SPOT_EXPONENT                         = 0x1205;
    int GL_SRC_ALPHA                             = 0x0302;
    int GL_SRC_ALPHA_SATURATE                    = 0x0308;
    int GL_SRC_COLOR                             = 0x0300;
    int GL_STACK_OVERFLOW                        = 0x0503;
    int GL_STACK_UNDERFLOW                       = 0x0504;
    int GL_STENCIL_BITS                          = 0x0D57;
    int GL_STENCIL_BUFFER_BIT                    = 0x0400;
    int GL_STENCIL_TEST                          = 0x0B90;
    int GL_SUBPIXEL_BITS                         = 0x0D50;
    int GL_TEXTURE                               = 0x1702;
    int GL_TEXTURE_2D                            = 0x0DE1;
    int GL_TEXTURE_COORD_ARRAY                   = 0x8078;
    int GL_TEXTURE_ENV                           = 0x2300;
    int GL_TEXTURE_ENV_COLOR                     = 0x2201;
    int GL_TEXTURE_ENV_MODE                      = 0x2200;
    int GL_TEXTURE_MAG_FILTER                    = 0x2800;
    int GL_TEXTURE_MIN_FILTER                    = 0x2801;
    int GL_TEXTURE_WRAP_S                        = 0x2802;
    int GL_TEXTURE_WRAP_T                        = 0x2803;
    int GL_TEXTURE0                              = 0x84C0;
    int GL_TEXTURE1                              = 0x84C1;
    int GL_TEXTURE2                              = 0x84C2;
    int GL_TEXTURE3                              = 0x84C3;
    int GL_TEXTURE4                              = 0x84C4;
    int GL_TEXTURE5                              = 0x84C5;
    int GL_TEXTURE6                              = 0x84C6;
    int GL_TEXTURE7                              = 0x84C7;
    int GL_TEXTURE8                              = 0x84C8;
    int GL_TEXTURE9                              = 0x84C9;
    int GL_TEXTURE10                             = 0x84CA;
    int GL_TEXTURE11                             = 0x84CB;
    int GL_TEXTURE12                             = 0x84CC;
    int GL_TEXTURE13                             = 0x84CD;
    int GL_TEXTURE14                             = 0x84CE;
    int GL_TEXTURE15                             = 0x84CF;
    int GL_TEXTURE16                             = 0x84D0;
    int GL_TEXTURE17                             = 0x84D1;
    int GL_TEXTURE18                             = 0x84D2;
    int GL_TEXTURE19                             = 0x84D3;
    int GL_TEXTURE20                             = 0x84D4;
    int GL_TEXTURE21                             = 0x84D5;
    int GL_TEXTURE22                             = 0x84D6;
    int GL_TEXTURE23                             = 0x84D7;
    int GL_TEXTURE24                             = 0x84D8;
    int GL_TEXTURE25                             = 0x84D9;
    int GL_TEXTURE26                             = 0x84DA;
    int GL_TEXTURE27                             = 0x84DB;
    int GL_TEXTURE28                             = 0x84DC;
    int GL_TEXTURE29                             = 0x84DD;
    int GL_TEXTURE30                             = 0x84DE;
    int GL_TEXTURE31                             = 0x84DF;
    int GL_TRIANGLE_FAN                          = 0x0006;
    int GL_TRIANGLE_STRIP                        = 0x0005;
    int GL_TRIANGLES                             = 0x0004;
    int GL_TRUE                                  = 1;
    int GL_UNPACK_ALIGNMENT                      = 0x0CF5;
    int GL_UNSIGNED_BYTE                         = 0x1401;
    int GL_UNSIGNED_SHORT                        = 0x1403;
    int GL_UNSIGNED_SHORT_4_4_4_4                = 0x8033;
    int GL_UNSIGNED_SHORT_5_5_5_1                = 0x8034;
    int GL_UNSIGNED_SHORT_5_6_5                  = 0x8363;
    int GL_VENDOR                                = 0x1F00;
    int GL_VERSION                               = 0x1F02;
    int GL_VERTEX_ARRAY                          = 0x8074;
    int GL_XOR                                   = 0x1506;
    int GL_ZERO                                  = 0;

    void glActiveTexture(
        int texture
    );

    void glAlphaFunc(
        int func,
        float ref
    );

    void glAlphaFuncx(
        int func,
        int ref
    );

    void glBindTexture(
        int target,
        int texture
    );

    void glBlendFunc(
        int sfactor,
        int dfactor
    );

    void glClear(
        int mask
    );

    void glClearColor(
        float red,
        float green,
        float blue,
        float alpha
    );

    void glClearColorx(
        int red,
        int green,
        int blue,
        int alpha
    );

    void glClearDepthf(
        float depth
    );

    void glClearDepthx(
        int depth
    );

    void glClearStencil(
        int s
    );

    void glClientActiveTexture(
        int texture
    );

    void glColor4f(
        float red,
        float green,
        float blue,
        float alpha
    );

    void glColor4x(
        int red,
        int green,
        int blue,
        int alpha
    );

    void glColorMask(
        boolean red,
        boolean green,
        boolean blue,
        boolean alpha
    );

    void glColorPointer(
        int size,
        int type,
        int stride,
        java.nio.Buffer pointer
    );

    void glCompressedTexImage2D(
        int target,
        int level,
        int internalformat,
        int width,
        int height,
        int border,
        int imageSize,
        java.nio.Buffer data
    );

    void glCompressedTexSubImage2D(
        int target,
        int level,
        int xoffset,
        int yoffset,
        int width,
        int height,
        int format,
        int imageSize,
        java.nio.Buffer data
    );

    void glCopyTexImage2D(
        int target,
        int level,
        int internalformat,
        int x,
        int y,
        int width,
        int height,
        int border
    );

    void glCopyTexSubImage2D(
        int target,
        int level,
        int xoffset,
        int yoffset,
        int x,
        int y,
        int width,
        int height
    );

    void glCullFace(
        int mode
    );

    void glDeleteTextures(
        int n,
        int[] textures,
        int offset
    );

    void glDeleteTextures(
        int n,
        java.nio.IntBuffer textures
    );

    void glDepthFunc(
        int func
    );

    void glDepthMask(
        boolean flag
    );

    void glDepthRangef(
        float zNear,
        float zFar
    );

    void glDepthRangex(
        int zNear,
        int zFar
    );

    void glDisable(
        int cap
    );

    void glDisableClientState(
        int array
    );

    void glDrawArrays(
        int mode,
        int first,
        int count
    );

    void glDrawElements(
        int mode,
        int count,
        int type,
        java.nio.Buffer indices
    );

    void glEnable(
        int cap
    );

    void glEnableClientState(
        int array
    );

    void glFinish(
    );

    void glFlush(
    );

    void glFogf(
        int pname,
        float param
    );

    void glFogfv(
        int pname,
        float[] params,
        int offset
    );

    void glFogfv(
        int pname,
        java.nio.FloatBuffer params
    );

    void glFogx(
        int pname,
        int param
    );

    void glFogxv(
        int pname,
        int[] params,
        int offset
    );

    void glFogxv(
        int pname,
        java.nio.IntBuffer params
    );

    void glFrontFace(
        int mode
    );

    void glFrustumf(
        float left,
        float right,
        float bottom,
        float top,
        float zNear,
        float zFar
    );

    void glFrustumx(
        int left,
        int right,
        int bottom,
        int top,
        int zNear,
        int zFar
    );

    void glGenTextures(
        int n,
        int[] textures,
        int offset
    );

    void glGenTextures(
        int n,
        java.nio.IntBuffer textures
    );

    int glGetError(
    );

    void glGetIntegerv(
        int pname,
        int[] params,
        int offset
    );

    void glGetIntegerv(
        int pname,
        java.nio.IntBuffer params
    );

    public String glGetString(
        int name
    );

    void glHint(
        int target,
        int mode
    );

    void glLightModelf(
        int pname,
        float param
    );

    void glLightModelfv(
        int pname,
        float[] params,
        int offset
    );

    void glLightModelfv(
        int pname,
        java.nio.FloatBuffer params
    );

    void glLightModelx(
        int pname,
        int param
    );

    void glLightModelxv(
        int pname,
        int[] params,
        int offset
    );

    void glLightModelxv(
        int pname,
        java.nio.IntBuffer params
    );

    void glLightf(
        int light,
        int pname,
        float param
    );

    void glLightfv(
        int light,
        int pname,
        float[] params,
        int offset
    );

    void glLightfv(
        int light,
        int pname,
        java.nio.FloatBuffer params
    );

    void glLightx(
        int light,
        int pname,
        int param
    );

    void glLightxv(
        int light,
        int pname,
        int[] params,
        int offset
    );

    void glLightxv(
        int light,
        int pname,
        java.nio.IntBuffer params
    );

    void glLineWidth(
        float width
    );

    void glLineWidthx(
        int width
    );

    void glLoadIdentity(
    );

    void glLoadMatrixf(
        float[] m,
        int offset
    );

    void glLoadMatrixf(
        java.nio.FloatBuffer m
    );

    void glLoadMatrixx(
        int[] m,
        int offset
    );

    void glLoadMatrixx(
        java.nio.IntBuffer m
    );

    void glLogicOp(
        int opcode
    );

    void glMaterialf(
        int face,
        int pname,
        float param
    );

    void glMaterialfv(
        int face,
        int pname,
        float[] params,
        int offset
    );

    void glMaterialfv(
        int face,
        int pname,
        java.nio.FloatBuffer params
    );

    void glMaterialx(
        int face,
        int pname,
        int param
    );

    void glMaterialxv(
        int face,
        int pname,
        int[] params,
        int offset
    );

    void glMaterialxv(
        int face,
        int pname,
        java.nio.IntBuffer params
    );

    void glMatrixMode(
        int mode
    );

    void glMultMatrixf(
        float[] m,
        int offset
    );

    void glMultMatrixf(
        java.nio.FloatBuffer m
    );

    void glMultMatrixx(
        int[] m,
        int offset
    );

    void glMultMatrixx(
        java.nio.IntBuffer m
    );

    void glMultiTexCoord4f(
        int target,
        float s,
        float t,
        float r,
        float q
    );

    void glMultiTexCoord4x(
        int target,
        int s,
        int t,
        int r,
        int q
    );

    void glNormal3f(
        float nx,
        float ny,
        float nz
    );

    void glNormal3x(
        int nx,
        int ny,
        int nz
    );

    void glNormalPointer(
        int type,
        int stride,
        java.nio.Buffer pointer
    );

    void glOrthof(
        float left,
        float right,
        float bottom,
        float top,
        float zNear,
        float zFar
    );

    void glOrthox(
        int left,
        int right,
        int bottom,
        int top,
        int zNear,
        int zFar
    );

    void glPixelStorei(
        int pname,
        int param
    );

    void glPointSize(
        float size
    );

    void glPointSizex(
        int size
    );

    void glPolygonOffset(
        float factor,
        float units
    );

    void glPolygonOffsetx(
        int factor,
        int units
    );

    void glPopMatrix(
    );

    void glPushMatrix(
    );

    void glReadPixels(
        int x,
        int y,
        int width,
        int height,
        int format,
        int type,
        java.nio.Buffer pixels
    );

    void glRotatef(
        float angle,
        float x,
        float y,
        float z
    );

    void glRotatex(
        int angle,
        int x,
        int y,
        int z
    );

    void glSampleCoverage(
        float value,
        boolean invert
    );

    void glSampleCoveragex(
        int value,
        boolean invert
    );

    void glScalef(
        float x,
        float y,
        float z
    );

    void glScalex(
        int x,
        int y,
        int z
    );

    void glScissor(
        int x,
        int y,
        int width,
        int height
    );

    void glShadeModel(
        int mode
    );

    void glStencilFunc(
        int func,
        int ref,
        int mask
    );

    void glStencilMask(
        int mask
    );

    void glStencilOp(
        int fail,
        int zfail,
        int zpass
    );

    void glTexCoordPointer(
        int size,
        int type,
        int stride,
        java.nio.Buffer pointer
    );

    void glTexEnvf(
        int target,
        int pname,
        float param
    );

    void glTexEnvfv(
        int target,
        int pname,
        float[] params,
        int offset
    );

    void glTexEnvfv(
        int target,
        int pname,
        java.nio.FloatBuffer params
    );

    void glTexEnvx(
        int target,
        int pname,
        int param
    );

    void glTexEnvxv(
        int target,
        int pname,
        int[] params,
        int offset
    );

    void glTexEnvxv(
        int target,
        int pname,
        java.nio.IntBuffer params
    );

    void glTexImage2D(
        int target,
        int level,
        int internalformat,
        int width,
        int height,
        int border,
        int format,
        int type,
        java.nio.Buffer pixels
    );

    void glTexParameterf(
        int target,
        int pname,
        float param
    );

    void glTexParameterx(
        int target,
        int pname,
        int param
    );

    void glTexSubImage2D(
        int target,
        int level,
        int xoffset,
        int yoffset,
        int width,
        int height,
        int format,
        int type,
        java.nio.Buffer pixels
    );

    void glTranslatef(
        float x,
        float y,
        float z
    );

    void glTranslatex(
        int x,
        int y,
        int z
    );

    void glVertexPointer(
        int size,
        int type,
        int stride,
        java.nio.Buffer pointer
    );

    void glViewport(
        int x,
        int y,
        int width,
        int height
    );

}
