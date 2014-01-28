/* //device/java/android/javax/microedition/khronos/opengles/GL11.java
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

public interface GL11 extends GL10 {
    int GL_ACTIVE_TEXTURE                          = 0x84E0;
    int GL_ADD_SIGNED                              = 0x8574;
    int GL_ALPHA_SCALE                             = 0x0D1C;
    int GL_ALPHA_TEST_FUNC                         = 0x0BC1;
    int GL_ALPHA_TEST_REF                          = 0x0BC2;
    int GL_ARRAY_BUFFER                            = 0x8892;
    int GL_ARRAY_BUFFER_BINDING                    = 0x8894;
    int GL_BLEND_DST                               = 0x0BE0;
    int GL_BLEND_SRC                               = 0x0BE1;
    int GL_BUFFER_ACCESS                           = 0x88BB;
    int GL_BUFFER_SIZE                             = 0x8764;
    int GL_BUFFER_USAGE                            = 0x8765;
    int GL_CLIENT_ACTIVE_TEXTURE                   = 0x84E1;
    int GL_CLIP_PLANE0                             = 0x3000;
    int GL_CLIP_PLANE1                             = 0x3001;
    int GL_CLIP_PLANE2                             = 0x3002;
    int GL_CLIP_PLANE3                             = 0x3003;
    int GL_CLIP_PLANE4                             = 0x3004;
    int GL_CLIP_PLANE5                             = 0x3005;
    int GL_COLOR_ARRAY_BUFFER_BINDING              = 0x8898;
    int GL_COLOR_ARRAY_POINTER                     = 0x8090;
    int GL_COLOR_ARRAY_SIZE                        = 0x8081;
    int GL_COLOR_ARRAY_STRIDE                      = 0x8083;
    int GL_COLOR_ARRAY_TYPE                        = 0x8082;
    int GL_COLOR_CLEAR_VALUE                       = 0x0C22;
    int GL_COLOR_WRITEMASK                         = 0x0C23;
    int GL_COMBINE                                 = 0x8570;
    int GL_COMBINE_ALPHA                           = 0x8572;
    int GL_COMBINE_RGB                             = 0x8571;
    int GL_CONSTANT                                = 0x8576;
    int GL_COORD_REPLACE_OES                       = 0x8862;
    int GL_CULL_FACE_MODE                          = 0x0B45;
    int GL_CURRENT_COLOR                           = 0x0B00;
    int GL_CURRENT_NORMAL                          = 0x0B02;
    int GL_CURRENT_TEXTURE_COORDS                  = 0x0B03;
    int GL_DEPTH_CLEAR_VALUE                       = 0x0B73;
    int GL_DEPTH_FUNC                              = 0x0B74;
    int GL_DEPTH_RANGE                             = 0x0B70;
    int GL_DEPTH_WRITEMASK                         = 0x0B72;
    int GL_DOT3_RGB                                = 0x86AE;
    int GL_DOT3_RGBA                               = 0x86AF;
    int GL_DYNAMIC_DRAW                            = 0x88E8;
    int GL_ELEMENT_ARRAY_BUFFER                    = 0x8893;
    int GL_ELEMENT_ARRAY_BUFFER_BINDING            = 0x8895;
    int GL_FRONT_FACE                              = 0x0B46;
    int GL_GENERATE_MIPMAP                         = 0x8191;
    int GL_GENERATE_MIPMAP_HINT                    = 0x8192;
    int GL_INTERPOLATE                             = 0x8575;
    int GL_LINE_WIDTH                              = 0x0B21;
    int GL_LOGIC_OP_MODE                           = 0x0BF0;
    int GL_MATRIX_MODE                             = 0x0BA0;
    int GL_MAX_CLIP_PLANES                         = 0x0D32;
    int GL_MODELVIEW_MATRIX                        = 0x0BA6;
    int GL_MODELVIEW_MATRIX_FLOAT_AS_INT_BITS_OES  = 0x898D;
    int GL_MODELVIEW_STACK_DEPTH                   = 0x0BA3;
    int GL_NORMAL_ARRAY_BUFFER_BINDING             = 0x8897;
    int GL_NORMAL_ARRAY_POINTER                    = 0x808F;
    int GL_NORMAL_ARRAY_STRIDE                     = 0x807F;
    int GL_NORMAL_ARRAY_TYPE                       = 0x807E;
    int GL_OPERAND0_ALPHA                          = 0x8598;
    int GL_OPERAND0_RGB                            = 0x8590;
    int GL_OPERAND1_ALPHA                          = 0x8599;
    int GL_OPERAND1_RGB                            = 0x8591;
    int GL_OPERAND2_ALPHA                          = 0x859A;
    int GL_OPERAND2_RGB                            = 0x8592;
    int GL_POINT_DISTANCE_ATTENUATION              = 0x8129;
    int GL_POINT_FADE_THRESHOLD_SIZE               = 0x8128;
    int GL_POINT_SIZE                              = 0x0B11;
    int GL_POINT_SIZE_ARRAY_BUFFER_BINDING_OES     = 0x8B9F;
    int GL_POINT_SIZE_ARRAY_OES                    = 0x8B9C;
    int GL_POINT_SIZE_ARRAY_POINTER_OES            = 0x898C;
    int GL_POINT_SIZE_ARRAY_STRIDE_OES             = 0x898B;
    int GL_POINT_SIZE_ARRAY_TYPE_OES               = 0x898A;
    int GL_POINT_SIZE_MAX                          = 0x8127;
    int GL_POINT_SIZE_MIN                          = 0x8126;
    int GL_POINT_SPRITE_OES                        = 0x8861;
    int GL_POLYGON_OFFSET_FACTOR                   = 0x8038;
    int GL_POLYGON_OFFSET_UNITS                    = 0x2A00;
    int GL_PREVIOUS                                = 0x8578;
    int GL_PRIMARY_COLOR                           = 0x8577;
    int GL_PROJECTION_MATRIX                       = 0x0BA7;
    int GL_PROJECTION_MATRIX_FLOAT_AS_INT_BITS_OES = 0x898E;
    int GL_PROJECTION_STACK_DEPTH                  = 0x0BA4;
    int GL_RGB_SCALE                               = 0x8573;
    int GL_SAMPLE_BUFFERS                          = 0x80A8;
    int GL_SAMPLE_COVERAGE_INVERT                  = 0x80AB;
    int GL_SAMPLE_COVERAGE_VALUE                   = 0x80AA;
    int GL_SAMPLES                                 = 0x80A9;
    int GL_SCISSOR_BOX                             = 0x0C10;
    int GL_SHADE_MODEL                             = 0x0B54;
    int GL_SRC0_ALPHA                              = 0x8588;
    int GL_SRC0_RGB                                = 0x8580;
    int GL_SRC1_ALPHA                              = 0x8589;
    int GL_SRC1_RGB                                = 0x8581;
    int GL_SRC2_ALPHA                              = 0x858A;
    int GL_SRC2_RGB                                = 0x8582;
    int GL_STATIC_DRAW                             = 0x88E4;
    int GL_STENCIL_CLEAR_VALUE                     = 0x0B91;
    int GL_STENCIL_FAIL                            = 0x0B94;
    int GL_STENCIL_FUNC                            = 0x0B92;
    int GL_STENCIL_PASS_DEPTH_FAIL                 = 0x0B95;
    int GL_STENCIL_PASS_DEPTH_PASS                 = 0x0B96;
    int GL_STENCIL_REF                             = 0x0B97;
    int GL_STENCIL_VALUE_MASK                      = 0x0B93;
    int GL_STENCIL_WRITEMASK                       = 0x0B98;
    int GL_SUBTRACT                                = 0x84E7;
    int GL_TEXTURE_BINDING_2D                      = 0x8069;
    int GL_TEXTURE_COORD_ARRAY_BUFFER_BINDING      = 0x889A;
    int GL_TEXTURE_COORD_ARRAY_POINTER             = 0x8092;
    int GL_TEXTURE_COORD_ARRAY_SIZE                = 0x8088;
    int GL_TEXTURE_COORD_ARRAY_STRIDE              = 0x808A;
    int GL_TEXTURE_COORD_ARRAY_TYPE                = 0x8089;
    int GL_TEXTURE_MATRIX                          = 0x0BA8;
    int GL_TEXTURE_MATRIX_FLOAT_AS_INT_BITS_OES    = 0x898F;
    int GL_TEXTURE_STACK_DEPTH                     = 0x0BA5;
    int GL_VERTEX_ARRAY_BUFFER_BINDING             = 0x8896;
    int GL_VERTEX_ARRAY_POINTER                    = 0x808E;
    int GL_VERTEX_ARRAY_SIZE                       = 0x807A;
    int GL_VERTEX_ARRAY_STRIDE                     = 0x807C;
    int GL_VERTEX_ARRAY_TYPE                       = 0x807B;
    int GL_VIEWPORT                                = 0x0BA2;
    int GL_WRITE_ONLY                              = 0x88B9;

    void glGetPointerv(int pname, java.nio.Buffer[] params);
    void glBindBuffer(
        int target,
        int buffer
    );

    void glBufferData(
        int target,
        int size,
        java.nio.Buffer data,
        int usage
    );

    void glBufferSubData(
        int target,
        int offset,
        int size,
        java.nio.Buffer data
    );

    void glClipPlanef(
        int plane,
        float[] equation,
        int offset
    );

    void glClipPlanef(
        int plane,
        java.nio.FloatBuffer equation
    );

    void glClipPlanex(
        int plane,
        int[] equation,
        int offset
    );

    void glClipPlanex(
        int plane,
        java.nio.IntBuffer equation
    );

    void glColor4ub(
        byte red,
        byte green,
        byte blue,
        byte alpha
    );

    void glColorPointer(
        int size,
        int type,
        int stride,
        int offset
    );

    void glDeleteBuffers(
        int n,
        int[] buffers,
        int offset
    );

    void glDeleteBuffers(
        int n,
        java.nio.IntBuffer buffers
    );

    void glDrawElements(
        int mode,
        int count,
        int type,
        int offset
    );

    void glGenBuffers(
        int n,
        int[] buffers,
        int offset
    );

    void glGenBuffers(
        int n,
        java.nio.IntBuffer buffers
    );

    void glGetBooleanv(
        int pname,
        boolean[] params,
        int offset
    );

    void glGetBooleanv(
        int pname,
        java.nio.IntBuffer params
    );

    void glGetBufferParameteriv(
        int target,
        int pname,
        int[] params,
        int offset
    );

    void glGetBufferParameteriv(
        int target,
        int pname,
        java.nio.IntBuffer params
    );

    void glGetClipPlanef(
        int pname,
        float[] eqn,
        int offset
    );

    void glGetClipPlanef(
        int pname,
        java.nio.FloatBuffer eqn
    );

    void glGetClipPlanex(
        int pname,
        int[] eqn,
        int offset
    );

    void glGetClipPlanex(
        int pname,
        java.nio.IntBuffer eqn
    );

    void glGetFixedv(
        int pname,
        int[] params,
        int offset
    );

    void glGetFixedv(
        int pname,
        java.nio.IntBuffer params
    );

    void glGetFloatv(
        int pname,
        float[] params,
        int offset
    );

    void glGetFloatv(
        int pname,
        java.nio.FloatBuffer params
    );

    void glGetLightfv(
        int light,
        int pname,
        float[] params,
        int offset
    );

    void glGetLightfv(
        int light,
        int pname,
        java.nio.FloatBuffer params
    );

    void glGetLightxv(
        int light,
        int pname,
        int[] params,
        int offset
    );

    void glGetLightxv(
        int light,
        int pname,
        java.nio.IntBuffer params
    );

    void glGetMaterialfv(
        int face,
        int pname,
        float[] params,
        int offset
    );

    void glGetMaterialfv(
        int face,
        int pname,
        java.nio.FloatBuffer params
    );

    void glGetMaterialxv(
        int face,
        int pname,
        int[] params,
        int offset
    );

    void glGetMaterialxv(
        int face,
        int pname,
        java.nio.IntBuffer params
    );

    void glGetTexEnviv(
        int env,
        int pname,
        int[] params,
        int offset
    );

    void glGetTexEnviv(
        int env,
        int pname,
        java.nio.IntBuffer params
    );

    void glGetTexEnvxv(
        int env,
        int pname,
        int[] params,
        int offset
    );

    void glGetTexEnvxv(
        int env,
        int pname,
        java.nio.IntBuffer params
    );

    void glGetTexParameterfv(
        int target,
        int pname,
        float[] params,
        int offset
    );

    void glGetTexParameterfv(
        int target,
        int pname,
        java.nio.FloatBuffer params
    );

    void glGetTexParameteriv(
        int target,
        int pname,
        int[] params,
        int offset
    );

    void glGetTexParameteriv(
        int target,
        int pname,
        java.nio.IntBuffer params
    );

    void glGetTexParameterxv(
        int target,
        int pname,
        int[] params,
        int offset
    );

    void glGetTexParameterxv(
        int target,
        int pname,
        java.nio.IntBuffer params
    );

    boolean glIsBuffer(
        int buffer
    );

    boolean glIsEnabled(
        int cap
    );

    boolean glIsTexture(
        int texture
    );

    void glNormalPointer(
        int type,
        int stride,
        int offset
    );

    void glPointParameterf(
        int pname,
        float param
    );

    void glPointParameterfv(
        int pname,
        float[] params,
        int offset
    );

    void glPointParameterfv(
        int pname,
        java.nio.FloatBuffer params
    );

    void glPointParameterx(
        int pname,
        int param
    );

    void glPointParameterxv(
        int pname,
        int[] params,
        int offset
    );

    void glPointParameterxv(
        int pname,
        java.nio.IntBuffer params
    );

    void glPointSizePointerOES(
        int type,
        int stride,
        java.nio.Buffer pointer
    );

    void glTexCoordPointer(
        int size,
        int type,
        int stride,
        int offset
    );

    void glTexEnvi(
        int target,
        int pname,
        int param
    );

    void glTexEnviv(
        int target,
        int pname,
        int[] params,
        int offset
    );

    void glTexEnviv(
        int target,
        int pname,
        java.nio.IntBuffer params
    );

    void glTexParameterfv(
        int target,
        int pname,
        float[] params,
        int offset
    );

    void glTexParameterfv(
        int target,
        int pname,
        java.nio.FloatBuffer params
    );

    void glTexParameteri(
        int target,
        int pname,
        int param
    );

    void glTexParameteriv(
        int target,
        int pname,
        int[] params,
        int offset
    );

    void glTexParameteriv(
        int target,
        int pname,
        java.nio.IntBuffer params
    );

    void glTexParameterxv(
        int target,
        int pname,
        int[] params,
        int offset
    );

    void glTexParameterxv(
        int target,
        int pname,
        java.nio.IntBuffer params
    );

    void glVertexPointer(
        int size,
        int type,
        int stride,
        int offset
    );

}
