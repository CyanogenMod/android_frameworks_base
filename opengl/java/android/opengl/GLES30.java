/*
**
** Copyright 2013, The Android Open Source Project
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

package android.opengl;

/** OpenGL ES 3.0
 */
public class GLES30 extends GLES20 {
    public static final int GL_READ_BUFFER                             = 0x0C02;
    public static final int GL_UNPACK_ROW_LENGTH                       = 0x0CF2;
    public static final int GL_UNPACK_SKIP_ROWS                        = 0x0CF3;
    public static final int GL_UNPACK_SKIP_PIXELS                      = 0x0CF4;
    public static final int GL_PACK_ROW_LENGTH                         = 0x0D02;
    public static final int GL_PACK_SKIP_ROWS                          = 0x0D03;
    public static final int GL_PACK_SKIP_PIXELS                        = 0x0D04;
    public static final int GL_COLOR                                   = 0x1800;
    public static final int GL_DEPTH                                   = 0x1801;
    public static final int GL_STENCIL                                 = 0x1802;
    public static final int GL_RED                                     = 0x1903;
    public static final int GL_RGB8                                    = 0x8051;
    public static final int GL_RGBA8                                   = 0x8058;
    public static final int GL_RGB10_A2                                = 0x8059;
    public static final int GL_TEXTURE_BINDING_3D                      = 0x806A;
    public static final int GL_UNPACK_SKIP_IMAGES                      = 0x806D;
    public static final int GL_UNPACK_IMAGE_HEIGHT                     = 0x806E;
    public static final int GL_TEXTURE_3D                              = 0x806F;
    public static final int GL_TEXTURE_WRAP_R                          = 0x8072;
    public static final int GL_MAX_3D_TEXTURE_SIZE                     = 0x8073;
    public static final int GL_UNSIGNED_INT_2_10_10_10_REV             = 0x8368;
    public static final int GL_MAX_ELEMENTS_VERTICES                   = 0x80E8;
    public static final int GL_MAX_ELEMENTS_INDICES                    = 0x80E9;
    public static final int GL_TEXTURE_MIN_LOD                         = 0x813A;
    public static final int GL_TEXTURE_MAX_LOD                         = 0x813B;
    public static final int GL_TEXTURE_BASE_LEVEL                      = 0x813C;
    public static final int GL_TEXTURE_MAX_LEVEL                       = 0x813D;
    public static final int GL_MIN                                     = 0x8007;
    public static final int GL_MAX                                     = 0x8008;
    public static final int GL_DEPTH_COMPONENT24                       = 0x81A6;
    public static final int GL_MAX_TEXTURE_LOD_BIAS                    = 0x84FD;
    public static final int GL_TEXTURE_COMPARE_MODE                    = 0x884C;
    public static final int GL_TEXTURE_COMPARE_FUNC                    = 0x884D;
    public static final int GL_CURRENT_QUERY                           = 0x8865;
    public static final int GL_QUERY_RESULT                            = 0x8866;
    public static final int GL_QUERY_RESULT_AVAILABLE                  = 0x8867;
    public static final int GL_BUFFER_MAPPED                           = 0x88BC;
    public static final int GL_BUFFER_MAP_POINTER                      = 0x88BD;
    public static final int GL_STREAM_READ                             = 0x88E1;
    public static final int GL_STREAM_COPY                             = 0x88E2;
    public static final int GL_STATIC_READ                             = 0x88E5;
    public static final int GL_STATIC_COPY                             = 0x88E6;
    public static final int GL_DYNAMIC_READ                            = 0x88E9;
    public static final int GL_DYNAMIC_COPY                            = 0x88EA;
    public static final int GL_MAX_DRAW_BUFFERS                        = 0x8824;
    public static final int GL_DRAW_BUFFER0                            = 0x8825;
    public static final int GL_DRAW_BUFFER1                            = 0x8826;
    public static final int GL_DRAW_BUFFER2                            = 0x8827;
    public static final int GL_DRAW_BUFFER3                            = 0x8828;
    public static final int GL_DRAW_BUFFER4                            = 0x8829;
    public static final int GL_DRAW_BUFFER5                            = 0x882A;
    public static final int GL_DRAW_BUFFER6                            = 0x882B;
    public static final int GL_DRAW_BUFFER7                            = 0x882C;
    public static final int GL_DRAW_BUFFER8                            = 0x882D;
    public static final int GL_DRAW_BUFFER9                            = 0x882E;
    public static final int GL_DRAW_BUFFER10                           = 0x882F;
    public static final int GL_DRAW_BUFFER11                           = 0x8830;
    public static final int GL_DRAW_BUFFER12                           = 0x8831;
    public static final int GL_DRAW_BUFFER13                           = 0x8832;
    public static final int GL_DRAW_BUFFER14                           = 0x8833;
    public static final int GL_DRAW_BUFFER15                           = 0x8834;
    public static final int GL_MAX_FRAGMENT_UNIFORM_COMPONENTS         = 0x8B49;
    public static final int GL_MAX_VERTEX_UNIFORM_COMPONENTS           = 0x8B4A;
    public static final int GL_SAMPLER_3D                              = 0x8B5F;
    public static final int GL_SAMPLER_2D_SHADOW                       = 0x8B62;
    public static final int GL_FRAGMENT_SHADER_DERIVATIVE_HINT         = 0x8B8B;
    public static final int GL_PIXEL_PACK_BUFFER                       = 0x88EB;
    public static final int GL_PIXEL_UNPACK_BUFFER                     = 0x88EC;
    public static final int GL_PIXEL_PACK_BUFFER_BINDING               = 0x88ED;
    public static final int GL_PIXEL_UNPACK_BUFFER_BINDING             = 0x88EF;
    public static final int GL_FLOAT_MAT2x3                            = 0x8B65;
    public static final int GL_FLOAT_MAT2x4                            = 0x8B66;
    public static final int GL_FLOAT_MAT3x2                            = 0x8B67;
    public static final int GL_FLOAT_MAT3x4                            = 0x8B68;
    public static final int GL_FLOAT_MAT4x2                            = 0x8B69;
    public static final int GL_FLOAT_MAT4x3                            = 0x8B6A;
    public static final int GL_SRGB                                    = 0x8C40;
    public static final int GL_SRGB8                                   = 0x8C41;
    public static final int GL_SRGB8_ALPHA8                            = 0x8C43;
    public static final int GL_COMPARE_REF_TO_TEXTURE                  = 0x884E;
    public static final int GL_MAJOR_VERSION                           = 0x821B;
    public static final int GL_MINOR_VERSION                           = 0x821C;
    public static final int GL_NUM_EXTENSIONS                          = 0x821D;
    public static final int GL_RGBA32F                                 = 0x8814;
    public static final int GL_RGB32F                                  = 0x8815;
    public static final int GL_RGBA16F                                 = 0x881A;
    public static final int GL_RGB16F                                  = 0x881B;
    public static final int GL_VERTEX_ATTRIB_ARRAY_INTEGER             = 0x88FD;
    public static final int GL_MAX_ARRAY_TEXTURE_LAYERS                = 0x88FF;
    public static final int GL_MIN_PROGRAM_TEXEL_OFFSET                = 0x8904;
    public static final int GL_MAX_PROGRAM_TEXEL_OFFSET                = 0x8905;
    public static final int GL_MAX_VARYING_COMPONENTS                  = 0x8B4B;
    public static final int GL_TEXTURE_2D_ARRAY                        = 0x8C1A;
    public static final int GL_TEXTURE_BINDING_2D_ARRAY                = 0x8C1D;
    public static final int GL_R11F_G11F_B10F                          = 0x8C3A;
    public static final int GL_UNSIGNED_INT_10F_11F_11F_REV            = 0x8C3B;
    public static final int GL_RGB9_E5                                 = 0x8C3D;
    public static final int GL_UNSIGNED_INT_5_9_9_9_REV                = 0x8C3E;
    public static final int GL_TRANSFORM_FEEDBACK_VARYING_MAX_LENGTH   = 0x8C76;
    public static final int GL_TRANSFORM_FEEDBACK_BUFFER_MODE          = 0x8C7F;
    public static final int GL_MAX_TRANSFORM_FEEDBACK_SEPARATE_COMPONENTS   = 0x8C80;
    public static final int GL_TRANSFORM_FEEDBACK_VARYINGS             = 0x8C83;
    public static final int GL_TRANSFORM_FEEDBACK_BUFFER_START         = 0x8C84;
    public static final int GL_TRANSFORM_FEEDBACK_BUFFER_SIZE          = 0x8C85;
    public static final int GL_TRANSFORM_FEEDBACK_PRIMITIVES_WRITTEN   = 0x8C88;
    public static final int GL_RASTERIZER_DISCARD                      = 0x8C89;
    public static final int GL_MAX_TRANSFORM_FEEDBACK_INTERLEAVED_COMPONENTS    = 0x8C8A;
    public static final int GL_MAX_TRANSFORM_FEEDBACK_SEPARATE_ATTRIBS = 0x8C8B;
    public static final int GL_INTERLEAVED_ATTRIBS                     = 0x8C8C;
    public static final int GL_SEPARATE_ATTRIBS                        = 0x8C8D;
    public static final int GL_TRANSFORM_FEEDBACK_BUFFER               = 0x8C8E;
    public static final int GL_TRANSFORM_FEEDBACK_BUFFER_BINDING       = 0x8C8F;
    public static final int GL_RGBA32UI                                = 0x8D70;
    public static final int GL_RGB32UI                                 = 0x8D71;
    public static final int GL_RGBA16UI                                = 0x8D76;
    public static final int GL_RGB16UI                                 = 0x8D77;
    public static final int GL_RGBA8UI                                 = 0x8D7C;
    public static final int GL_RGB8UI                                  = 0x8D7D;
    public static final int GL_RGBA32I                                 = 0x8D82;
    public static final int GL_RGB32I                                  = 0x8D83;
    public static final int GL_RGBA16I                                 = 0x8D88;
    public static final int GL_RGB16I                                  = 0x8D89;
    public static final int GL_RGBA8I                                  = 0x8D8E;
    public static final int GL_RGB8I                                   = 0x8D8F;
    public static final int GL_RED_INTEGER                             = 0x8D94;
    public static final int GL_RGB_INTEGER                             = 0x8D98;
    public static final int GL_RGBA_INTEGER                            = 0x8D99;
    public static final int GL_SAMPLER_2D_ARRAY                        = 0x8DC1;
    public static final int GL_SAMPLER_2D_ARRAY_SHADOW                 = 0x8DC4;
    public static final int GL_SAMPLER_CUBE_SHADOW                     = 0x8DC5;
    public static final int GL_UNSIGNED_INT_VEC2                       = 0x8DC6;
    public static final int GL_UNSIGNED_INT_VEC3                       = 0x8DC7;
    public static final int GL_UNSIGNED_INT_VEC4                       = 0x8DC8;
    public static final int GL_INT_SAMPLER_2D                          = 0x8DCA;
    public static final int GL_INT_SAMPLER_3D                          = 0x8DCB;
    public static final int GL_INT_SAMPLER_CUBE                        = 0x8DCC;
    public static final int GL_INT_SAMPLER_2D_ARRAY                    = 0x8DCF;
    public static final int GL_UNSIGNED_INT_SAMPLER_2D                 = 0x8DD2;
    public static final int GL_UNSIGNED_INT_SAMPLER_3D                 = 0x8DD3;
    public static final int GL_UNSIGNED_INT_SAMPLER_CUBE               = 0x8DD4;
    public static final int GL_UNSIGNED_INT_SAMPLER_2D_ARRAY           = 0x8DD7;
    public static final int GL_BUFFER_ACCESS_FLAGS                     = 0x911F;
    public static final int GL_BUFFER_MAP_LENGTH                       = 0x9120;
    public static final int GL_BUFFER_MAP_OFFSET                       = 0x9121;
    public static final int GL_DEPTH_COMPONENT32F                      = 0x8CAC;
    public static final int GL_DEPTH32F_STENCIL8                       = 0x8CAD;
    public static final int GL_FLOAT_32_UNSIGNED_INT_24_8_REV          = 0x8DAD;
    public static final int GL_FRAMEBUFFER_ATTACHMENT_COLOR_ENCODING   = 0x8210;
    public static final int GL_FRAMEBUFFER_ATTACHMENT_COMPONENT_TYPE   = 0x8211;
    public static final int GL_FRAMEBUFFER_ATTACHMENT_RED_SIZE         = 0x8212;
    public static final int GL_FRAMEBUFFER_ATTACHMENT_GREEN_SIZE       = 0x8213;
    public static final int GL_FRAMEBUFFER_ATTACHMENT_BLUE_SIZE        = 0x8214;
    public static final int GL_FRAMEBUFFER_ATTACHMENT_ALPHA_SIZE       = 0x8215;
    public static final int GL_FRAMEBUFFER_ATTACHMENT_DEPTH_SIZE       = 0x8216;
    public static final int GL_FRAMEBUFFER_ATTACHMENT_STENCIL_SIZE     = 0x8217;
    public static final int GL_FRAMEBUFFER_DEFAULT                     = 0x8218;
    public static final int GL_FRAMEBUFFER_UNDEFINED                   = 0x8219;
    public static final int GL_DEPTH_STENCIL_ATTACHMENT                = 0x821A;
    public static final int GL_DEPTH_STENCIL                           = 0x84F9;
    public static final int GL_UNSIGNED_INT_24_8                       = 0x84FA;
    public static final int GL_DEPTH24_STENCIL8                        = 0x88F0;
    public static final int GL_UNSIGNED_NORMALIZED                     = 0x8C17;
    public static final int GL_DRAW_FRAMEBUFFER_BINDING                = GL_FRAMEBUFFER_BINDING;
    public static final int GL_READ_FRAMEBUFFER                        = 0x8CA8;
    public static final int GL_DRAW_FRAMEBUFFER                        = 0x8CA9;
    public static final int GL_READ_FRAMEBUFFER_BINDING                = 0x8CAA;
    public static final int GL_RENDERBUFFER_SAMPLES                    = 0x8CAB;
    public static final int GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LAYER    = 0x8CD4;
    public static final int GL_MAX_COLOR_ATTACHMENTS                   = 0x8CDF;
    public static final int GL_COLOR_ATTACHMENT1                       = 0x8CE1;
    public static final int GL_COLOR_ATTACHMENT2                       = 0x8CE2;
    public static final int GL_COLOR_ATTACHMENT3                       = 0x8CE3;
    public static final int GL_COLOR_ATTACHMENT4                       = 0x8CE4;
    public static final int GL_COLOR_ATTACHMENT5                       = 0x8CE5;
    public static final int GL_COLOR_ATTACHMENT6                       = 0x8CE6;
    public static final int GL_COLOR_ATTACHMENT7                       = 0x8CE7;
    public static final int GL_COLOR_ATTACHMENT8                       = 0x8CE8;
    public static final int GL_COLOR_ATTACHMENT9                       = 0x8CE9;
    public static final int GL_COLOR_ATTACHMENT10                      = 0x8CEA;
    public static final int GL_COLOR_ATTACHMENT11                      = 0x8CEB;
    public static final int GL_COLOR_ATTACHMENT12                      = 0x8CEC;
    public static final int GL_COLOR_ATTACHMENT13                      = 0x8CED;
    public static final int GL_COLOR_ATTACHMENT14                      = 0x8CEE;
    public static final int GL_COLOR_ATTACHMENT15                      = 0x8CEF;
    public static final int GL_FRAMEBUFFER_INCOMPLETE_MULTISAMPLE      = 0x8D56;
    public static final int GL_MAX_SAMPLES                             = 0x8D57;
    public static final int GL_HALF_FLOAT                              = 0x140B;
    public static final int GL_MAP_READ_BIT                            = 0x0001;
    public static final int GL_MAP_WRITE_BIT                           = 0x0002;
    public static final int GL_MAP_INVALIDATE_RANGE_BIT                = 0x0004;
    public static final int GL_MAP_INVALIDATE_BUFFER_BIT               = 0x0008;
    public static final int GL_MAP_FLUSH_EXPLICIT_BIT                  = 0x0010;
    public static final int GL_MAP_UNSYNCHRONIZED_BIT                  = 0x0020;
    public static final int GL_RG                                      = 0x8227;
    public static final int GL_RG_INTEGER                              = 0x8228;
    public static final int GL_R8                                      = 0x8229;
    public static final int GL_RG8                                     = 0x822B;
    public static final int GL_R16F                                    = 0x822D;
    public static final int GL_R32F                                    = 0x822E;
    public static final int GL_RG16F                                   = 0x822F;
    public static final int GL_RG32F                                   = 0x8230;
    public static final int GL_R8I                                     = 0x8231;
    public static final int GL_R8UI                                    = 0x8232;
    public static final int GL_R16I                                    = 0x8233;
    public static final int GL_R16UI                                   = 0x8234;
    public static final int GL_R32I                                    = 0x8235;
    public static final int GL_R32UI                                   = 0x8236;
    public static final int GL_RG8I                                    = 0x8237;
    public static final int GL_RG8UI                                   = 0x8238;
    public static final int GL_RG16I                                   = 0x8239;
    public static final int GL_RG16UI                                  = 0x823A;
    public static final int GL_RG32I                                   = 0x823B;
    public static final int GL_RG32UI                                  = 0x823C;
    public static final int GL_VERTEX_ARRAY_BINDING                    = 0x85B5;
    public static final int GL_R8_SNORM                                = 0x8F94;
    public static final int GL_RG8_SNORM                               = 0x8F95;
    public static final int GL_RGB8_SNORM                              = 0x8F96;
    public static final int GL_RGBA8_SNORM                             = 0x8F97;
    public static final int GL_SIGNED_NORMALIZED                       = 0x8F9C;
    public static final int GL_PRIMITIVE_RESTART_FIXED_INDEX           = 0x8D69;
    public static final int GL_COPY_READ_BUFFER                        = 0x8F36;
    public static final int GL_COPY_WRITE_BUFFER                       = 0x8F37;
    public static final int GL_COPY_READ_BUFFER_BINDING                = GL_COPY_READ_BUFFER;
    public static final int GL_COPY_WRITE_BUFFER_BINDING               = GL_COPY_WRITE_BUFFER;
    public static final int GL_UNIFORM_BUFFER                          = 0x8A11;
    public static final int GL_UNIFORM_BUFFER_BINDING                  = 0x8A28;
    public static final int GL_UNIFORM_BUFFER_START                    = 0x8A29;
    public static final int GL_UNIFORM_BUFFER_SIZE                     = 0x8A2A;
    public static final int GL_MAX_VERTEX_UNIFORM_BLOCKS               = 0x8A2B;
    public static final int GL_MAX_FRAGMENT_UNIFORM_BLOCKS             = 0x8A2D;
    public static final int GL_MAX_COMBINED_UNIFORM_BLOCKS             = 0x8A2E;
    public static final int GL_MAX_UNIFORM_BUFFER_BINDINGS             = 0x8A2F;
    public static final int GL_MAX_UNIFORM_BLOCK_SIZE                  = 0x8A30;
    public static final int GL_MAX_COMBINED_VERTEX_UNIFORM_COMPONENTS  = 0x8A31;
    public static final int GL_MAX_COMBINED_FRAGMENT_UNIFORM_COMPONENTS     = 0x8A33;
    public static final int GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT         = 0x8A34;
    public static final int GL_ACTIVE_UNIFORM_BLOCK_MAX_NAME_LENGTH    = 0x8A35;
    public static final int GL_ACTIVE_UNIFORM_BLOCKS                   = 0x8A36;
    public static final int GL_UNIFORM_TYPE                            = 0x8A37;
    public static final int GL_UNIFORM_SIZE                            = 0x8A38;
    public static final int GL_UNIFORM_NAME_LENGTH                     = 0x8A39;
    public static final int GL_UNIFORM_BLOCK_INDEX                     = 0x8A3A;
    public static final int GL_UNIFORM_OFFSET                          = 0x8A3B;
    public static final int GL_UNIFORM_ARRAY_STRIDE                    = 0x8A3C;
    public static final int GL_UNIFORM_MATRIX_STRIDE                   = 0x8A3D;
    public static final int GL_UNIFORM_IS_ROW_MAJOR                    = 0x8A3E;
    public static final int GL_UNIFORM_BLOCK_BINDING                   = 0x8A3F;
    public static final int GL_UNIFORM_BLOCK_DATA_SIZE                 = 0x8A40;
    public static final int GL_UNIFORM_BLOCK_NAME_LENGTH               = 0x8A41;
    public static final int GL_UNIFORM_BLOCK_ACTIVE_UNIFORMS           = 0x8A42;
    public static final int GL_UNIFORM_BLOCK_ACTIVE_UNIFORM_INDICES    = 0x8A43;
    public static final int GL_UNIFORM_BLOCK_REFERENCED_BY_VERTEX_SHADER    = 0x8A44;
    public static final int GL_UNIFORM_BLOCK_REFERENCED_BY_FRAGMENT_SHADER  = 0x8A46;
    // GL_INVALID_INDEX is defined as 0xFFFFFFFFu in C.
    public static final int GL_INVALID_INDEX                           = -1;
    public static final int GL_MAX_VERTEX_OUTPUT_COMPONENTS            = 0x9122;
    public static final int GL_MAX_FRAGMENT_INPUT_COMPONENTS           = 0x9125;
    public static final int GL_MAX_SERVER_WAIT_TIMEOUT                 = 0x9111;
    public static final int GL_OBJECT_TYPE                             = 0x9112;
    public static final int GL_SYNC_CONDITION                          = 0x9113;
    public static final int GL_SYNC_STATUS                             = 0x9114;
    public static final int GL_SYNC_FLAGS                              = 0x9115;
    public static final int GL_SYNC_FENCE                              = 0x9116;
    public static final int GL_SYNC_GPU_COMMANDS_COMPLETE              = 0x9117;
    public static final int GL_UNSIGNALED                              = 0x9118;
    public static final int GL_SIGNALED                                = 0x9119;
    public static final int GL_ALREADY_SIGNALED                        = 0x911A;
    public static final int GL_TIMEOUT_EXPIRED                         = 0x911B;
    public static final int GL_CONDITION_SATISFIED                     = 0x911C;
    public static final int GL_WAIT_FAILED                             = 0x911D;
    public static final int GL_SYNC_FLUSH_COMMANDS_BIT                 = 0x00000001;
    // GL_TIMEOUT_IGNORED is defined as 0xFFFFFFFFFFFFFFFFull in C.
    public static final long GL_TIMEOUT_IGNORED                         = -1;
    public static final int GL_VERTEX_ATTRIB_ARRAY_DIVISOR             = 0x88FE;
    public static final int GL_ANY_SAMPLES_PASSED                      = 0x8C2F;
    public static final int GL_ANY_SAMPLES_PASSED_CONSERVATIVE         = 0x8D6A;
    public static final int GL_SAMPLER_BINDING                         = 0x8919;
    public static final int GL_RGB10_A2UI                              = 0x906F;
    public static final int GL_TEXTURE_SWIZZLE_R                       = 0x8E42;
    public static final int GL_TEXTURE_SWIZZLE_G                       = 0x8E43;
    public static final int GL_TEXTURE_SWIZZLE_B                       = 0x8E44;
    public static final int GL_TEXTURE_SWIZZLE_A                       = 0x8E45;
    public static final int GL_GREEN                                   = 0x1904;
    public static final int GL_BLUE                                    = 0x1905;
    public static final int GL_INT_2_10_10_10_REV                      = 0x8D9F;
    public static final int GL_TRANSFORM_FEEDBACK                      = 0x8E22;
    public static final int GL_TRANSFORM_FEEDBACK_PAUSED               = 0x8E23;
    public static final int GL_TRANSFORM_FEEDBACK_ACTIVE               = 0x8E24;
    public static final int GL_TRANSFORM_FEEDBACK_BINDING              = 0x8E25;
    public static final int GL_PROGRAM_BINARY_RETRIEVABLE_HINT         = 0x8257;
    public static final int GL_PROGRAM_BINARY_LENGTH                   = 0x8741;
    public static final int GL_NUM_PROGRAM_BINARY_FORMATS              = 0x87FE;
    public static final int GL_PROGRAM_BINARY_FORMATS                  = 0x87FF;
    public static final int GL_COMPRESSED_R11_EAC                      = 0x9270;
    public static final int GL_COMPRESSED_SIGNED_R11_EAC               = 0x9271;
    public static final int GL_COMPRESSED_RG11_EAC                     = 0x9272;
    public static final int GL_COMPRESSED_SIGNED_RG11_EAC              = 0x9273;
    public static final int GL_COMPRESSED_RGB8_ETC2                    = 0x9274;
    public static final int GL_COMPRESSED_SRGB8_ETC2                   = 0x9275;
    public static final int GL_COMPRESSED_RGB8_PUNCHTHROUGH_ALPHA1_ETC2     = 0x9276;
    public static final int GL_COMPRESSED_SRGB8_PUNCHTHROUGH_ALPHA1_ETC2    = 0x9277;
    public static final int GL_COMPRESSED_RGBA8_ETC2_EAC               = 0x9278;
    public static final int GL_COMPRESSED_SRGB8_ALPHA8_ETC2_EAC        = 0x9279;
    public static final int GL_TEXTURE_IMMUTABLE_FORMAT                = 0x912F;
    public static final int GL_MAX_ELEMENT_INDEX                       = 0x8D6B;
    public static final int GL_NUM_SAMPLE_COUNTS                       = 0x9380;
    public static final int GL_TEXTURE_IMMUTABLE_LEVELS                = 0x82DF;

    native private static void _nativeClassInit();
    static {
        _nativeClassInit();
    }

    // C function void glReadBuffer ( GLenum mode )

    public static native void glReadBuffer(
        int mode
    );

    // C function void glDrawRangeElements ( GLenum mode, GLuint start, GLuint end, GLsizei count, GLenum type, const GLvoid *indices )

    public static native void glDrawRangeElements(
        int mode,
        int start,
        int end,
        int count,
        int type,
        java.nio.Buffer indices
    );

    // C function void glDrawRangeElements ( GLenum mode, GLuint start, GLuint end, GLsizei count, GLenum type, GLsizei offset )

    public static native void glDrawRangeElements(
        int mode,
        int start,
        int end,
        int count,
        int type,
        int offset
    );

    // C function void glTexImage3D ( GLenum target, GLint level, GLint internalformat, GLsizei width, GLsizei height, GLsizei depth, GLint border, GLenum format, GLenum type, const GLvoid *pixels )

    public static native void glTexImage3D(
        int target,
        int level,
        int internalformat,
        int width,
        int height,
        int depth,
        int border,
        int format,
        int type,
        java.nio.Buffer pixels
    );

    // C function void glTexImage3D ( GLenum target, GLint level, GLint internalformat, GLsizei width, GLsizei height, GLsizei depth, GLint border, GLenum format, GLenum type, GLsizei offset )

    public static native void glTexImage3D(
        int target,
        int level,
        int internalformat,
        int width,
        int height,
        int depth,
        int border,
        int format,
        int type,
        int offset
    );

    // C function void glTexSubImage3D ( GLenum target, GLint level, GLint xoffset, GLint yoffset, GLint zoffset, GLsizei width, GLsizei height, GLsizei depth, GLenum format, GLenum type, const GLvoid *pixels )

    public static native void glTexSubImage3D(
        int target,
        int level,
        int xoffset,
        int yoffset,
        int zoffset,
        int width,
        int height,
        int depth,
        int format,
        int type,
        java.nio.Buffer pixels
    );

    // C function void glTexSubImage3D ( GLenum target, GLint level, GLint xoffset, GLint yoffset, GLint zoffset, GLsizei width, GLsizei height, GLsizei depth, GLenum format, GLenum type, GLsizei offset )

    public static native void glTexSubImage3D(
        int target,
        int level,
        int xoffset,
        int yoffset,
        int zoffset,
        int width,
        int height,
        int depth,
        int format,
        int type,
        int offset
    );

    // C function void glCopyTexSubImage3D ( GLenum target, GLint level, GLint xoffset, GLint yoffset, GLint zoffset, GLint x, GLint y, GLsizei width, GLsizei height )

    public static native void glCopyTexSubImage3D(
        int target,
        int level,
        int xoffset,
        int yoffset,
        int zoffset,
        int x,
        int y,
        int width,
        int height
    );

    // C function void glCompressedTexImage3D ( GLenum target, GLint level, GLenum internalformat, GLsizei width, GLsizei height, GLsizei depth, GLint border, GLsizei imageSize, const GLvoid *data )

    public static native void glCompressedTexImage3D(
        int target,
        int level,
        int internalformat,
        int width,
        int height,
        int depth,
        int border,
        int imageSize,
        java.nio.Buffer data
    );

    // C function void glCompressedTexImage3D ( GLenum target, GLint level, GLenum internalformat, GLsizei width, GLsizei height, GLsizei depth, GLint border, GLsizei imageSize, GLsizei offset )

    public static native void glCompressedTexImage3D(
        int target,
        int level,
        int internalformat,
        int width,
        int height,
        int depth,
        int border,
        int imageSize,
        int offset
    );

    // C function void glCompressedTexSubImage3D ( GLenum target, GLint level, GLint xoffset, GLint yoffset, GLint zoffset, GLsizei width, GLsizei height, GLsizei depth, GLenum format, GLsizei imageSize, const GLvoid *data )

    public static native void glCompressedTexSubImage3D(
        int target,
        int level,
        int xoffset,
        int yoffset,
        int zoffset,
        int width,
        int height,
        int depth,
        int format,
        int imageSize,
        java.nio.Buffer data
    );

    // C function void glCompressedTexSubImage3D ( GLenum target, GLint level, GLint xoffset, GLint yoffset, GLint zoffset, GLsizei width, GLsizei height, GLsizei depth, GLenum format, GLsizei imageSize, GLsizei offset )

    public static native void glCompressedTexSubImage3D(
        int target,
        int level,
        int xoffset,
        int yoffset,
        int zoffset,
        int width,
        int height,
        int depth,
        int format,
        int imageSize,
        int offset
    );

    // C function void glGenQueries ( GLsizei n, GLuint *ids )

    public static native void glGenQueries(
        int n,
        int[] ids,
        int offset
    );

    // C function void glGenQueries ( GLsizei n, GLuint *ids )

    public static native void glGenQueries(
        int n,
        java.nio.IntBuffer ids
    );

    // C function void glDeleteQueries ( GLsizei n, const GLuint *ids )

    public static native void glDeleteQueries(
        int n,
        int[] ids,
        int offset
    );

    // C function void glDeleteQueries ( GLsizei n, const GLuint *ids )

    public static native void glDeleteQueries(
        int n,
        java.nio.IntBuffer ids
    );

    // C function GLboolean glIsQuery ( GLuint id )

    public static native boolean glIsQuery(
        int id
    );

    // C function void glBeginQuery ( GLenum target, GLuint id )

    public static native void glBeginQuery(
        int target,
        int id
    );

    // C function void glEndQuery ( GLenum target )

    public static native void glEndQuery(
        int target
    );

    // C function void glGetQueryiv ( GLenum target, GLenum pname, GLint *params )

    public static native void glGetQueryiv(
        int target,
        int pname,
        int[] params,
        int offset
    );

    // C function void glGetQueryiv ( GLenum target, GLenum pname, GLint *params )

    public static native void glGetQueryiv(
        int target,
        int pname,
        java.nio.IntBuffer params
    );

    // C function void glGetQueryObjectuiv ( GLuint id, GLenum pname, GLuint *params )

    public static native void glGetQueryObjectuiv(
        int id,
        int pname,
        int[] params,
        int offset
    );

    // C function void glGetQueryObjectuiv ( GLuint id, GLenum pname, GLuint *params )

    public static native void glGetQueryObjectuiv(
        int id,
        int pname,
        java.nio.IntBuffer params
    );

    // C function GLboolean glUnmapBuffer ( GLenum target )

    public static native boolean glUnmapBuffer(
        int target
    );

    // C function void glGetBufferPointerv ( GLenum target, GLenum pname, GLvoid** params )

    public static native java.nio.Buffer glGetBufferPointerv(
        int target,
        int pname
    );

    // C function void glDrawBuffers ( GLsizei n, const GLenum *bufs )

    public static native void glDrawBuffers(
        int n,
        int[] bufs,
        int offset
    );

    // C function void glDrawBuffers ( GLsizei n, const GLenum *bufs )

    public static native void glDrawBuffers(
        int n,
        java.nio.IntBuffer bufs
    );

    // C function void glUniformMatrix2x3fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )

    public static native void glUniformMatrix2x3fv(
        int location,
        int count,
        boolean transpose,
        float[] value,
        int offset
    );

    // C function void glUniformMatrix2x3fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )

    public static native void glUniformMatrix2x3fv(
        int location,
        int count,
        boolean transpose,
        java.nio.FloatBuffer value
    );

    // C function void glUniformMatrix3x2fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )

    public static native void glUniformMatrix3x2fv(
        int location,
        int count,
        boolean transpose,
        float[] value,
        int offset
    );

    // C function void glUniformMatrix3x2fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )

    public static native void glUniformMatrix3x2fv(
        int location,
        int count,
        boolean transpose,
        java.nio.FloatBuffer value
    );

    // C function void glUniformMatrix2x4fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )

    public static native void glUniformMatrix2x4fv(
        int location,
        int count,
        boolean transpose,
        float[] value,
        int offset
    );

    // C function void glUniformMatrix2x4fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )

    public static native void glUniformMatrix2x4fv(
        int location,
        int count,
        boolean transpose,
        java.nio.FloatBuffer value
    );

    // C function void glUniformMatrix4x2fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )

    public static native void glUniformMatrix4x2fv(
        int location,
        int count,
        boolean transpose,
        float[] value,
        int offset
    );

    // C function void glUniformMatrix4x2fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )

    public static native void glUniformMatrix4x2fv(
        int location,
        int count,
        boolean transpose,
        java.nio.FloatBuffer value
    );

    // C function void glUniformMatrix3x4fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )

    public static native void glUniformMatrix3x4fv(
        int location,
        int count,
        boolean transpose,
        float[] value,
        int offset
    );

    // C function void glUniformMatrix3x4fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )

    public static native void glUniformMatrix3x4fv(
        int location,
        int count,
        boolean transpose,
        java.nio.FloatBuffer value
    );

    // C function void glUniformMatrix4x3fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )

    public static native void glUniformMatrix4x3fv(
        int location,
        int count,
        boolean transpose,
        float[] value,
        int offset
    );

    // C function void glUniformMatrix4x3fv ( GLint location, GLsizei count, GLboolean transpose, const GLfloat *value )

    public static native void glUniformMatrix4x3fv(
        int location,
        int count,
        boolean transpose,
        java.nio.FloatBuffer value
    );

    // C function void glBlitFramebuffer ( GLint srcX0, GLint srcY0, GLint srcX1, GLint srcY1, GLint dstX0, GLint dstY0, GLint dstX1, GLint dstY1, GLbitfield mask, GLenum filter )

    public static native void glBlitFramebuffer(
        int srcX0,
        int srcY0,
        int srcX1,
        int srcY1,
        int dstX0,
        int dstY0,
        int dstX1,
        int dstY1,
        int mask,
        int filter
    );

    // C function void glRenderbufferStorageMultisample ( GLenum target, GLsizei samples, GLenum internalformat, GLsizei width, GLsizei height )

    public static native void glRenderbufferStorageMultisample(
        int target,
        int samples,
        int internalformat,
        int width,
        int height
    );

    // C function void glFramebufferTextureLayer ( GLenum target, GLenum attachment, GLuint texture, GLint level, GLint layer )

    public static native void glFramebufferTextureLayer(
        int target,
        int attachment,
        int texture,
        int level,
        int layer
    );

    // C function GLvoid * glMapBufferRange ( GLenum target, GLintptr offset, GLsizeiptr length, GLbitfield access )

    public static native java.nio.Buffer glMapBufferRange(
        int target,
        int offset,
        int length,
        int access
    );

    // C function void glFlushMappedBufferRange ( GLenum target, GLintptr offset, GLsizeiptr length )

    public static native void glFlushMappedBufferRange(
        int target,
        int offset,
        int length
    );

    // C function void glBindVertexArray ( GLuint array )

    public static native void glBindVertexArray(
        int array
    );

    // C function void glDeleteVertexArrays ( GLsizei n, const GLuint *arrays )

    public static native void glDeleteVertexArrays(
        int n,
        int[] arrays,
        int offset
    );

    // C function void glDeleteVertexArrays ( GLsizei n, const GLuint *arrays )

    public static native void glDeleteVertexArrays(
        int n,
        java.nio.IntBuffer arrays
    );

    // C function void glGenVertexArrays ( GLsizei n, GLuint *arrays )

    public static native void glGenVertexArrays(
        int n,
        int[] arrays,
        int offset
    );

    // C function void glGenVertexArrays ( GLsizei n, GLuint *arrays )

    public static native void glGenVertexArrays(
        int n,
        java.nio.IntBuffer arrays
    );

    // C function GLboolean glIsVertexArray ( GLuint array )

    public static native boolean glIsVertexArray(
        int array
    );

    // C function void glGetIntegeri_v ( GLenum target, GLuint index, GLint *data )

    public static native void glGetIntegeri_v(
        int target,
        int index,
        int[] data,
        int offset
    );

    // C function void glGetIntegeri_v ( GLenum target, GLuint index, GLint *data )

    public static native void glGetIntegeri_v(
        int target,
        int index,
        java.nio.IntBuffer data
    );

    // C function void glBeginTransformFeedback ( GLenum primitiveMode )

    public static native void glBeginTransformFeedback(
        int primitiveMode
    );

    // C function void glEndTransformFeedback ( void )

    public static native void glEndTransformFeedback(
    );

    // C function void glBindBufferRange ( GLenum target, GLuint index, GLuint buffer, GLintptr offset, GLsizeiptr size )

    public static native void glBindBufferRange(
        int target,
        int index,
        int buffer,
        int offset,
        int size
    );

    // C function void glBindBufferBase ( GLenum target, GLuint index, GLuint buffer )

    public static native void glBindBufferBase(
        int target,
        int index,
        int buffer
    );

    // C function void glTransformFeedbackVaryings ( GLuint program, GLsizei count, const GLchar *varyings, GLenum bufferMode )

    public static native void glTransformFeedbackVaryings(
        int program,
        String[] varyings,
        int bufferMode
    );

    // C function void glGetTransformFeedbackVarying ( GLuint program, GLuint index, GLsizei bufSize, GLsizei *length, GLint *size, GLenum *type, GLchar *name )

    public static native void glGetTransformFeedbackVarying(
        int program,
        int index,
        int bufsize,
        int[] length,
        int lengthOffset,
        int[] size,
        int sizeOffset,
        int[] type,
        int typeOffset,
        byte[] name,
        int nameOffset
    );

    // C function void glGetTransformFeedbackVarying ( GLuint program, GLuint index, GLsizei bufSize, GLsizei *length, GLint *size, GLenum *type, GLchar *name )

    public static native void glGetTransformFeedbackVarying(
        int program,
        int index,
        int bufsize,
        java.nio.IntBuffer length,
        java.nio.IntBuffer size,
        java.nio.IntBuffer type,
        byte name
    );

    // C function void glGetTransformFeedbackVarying ( GLuint program, GLuint index, GLsizei bufSize, GLsizei *length, GLint *size, GLenum *type, GLchar *name )

    public static native String glGetTransformFeedbackVarying(
        int program,
        int index,
        int[] size,
        int sizeOffset,
        int[] type,
        int typeOffset
    );

    // C function void glGetTransformFeedbackVarying ( GLuint program, GLuint index, GLsizei bufSize, GLsizei *length, GLint *size, GLenum *type, GLchar *name )

    public static native String glGetTransformFeedbackVarying(
        int program,
        int index,
        java.nio.IntBuffer size,
        java.nio.IntBuffer type
    );

    // C function void glVertexAttribIPointer ( GLuint index, GLint size, GLenum type, GLsizei stride, const GLvoid *pointer )

    private static native void glVertexAttribIPointerBounds(
        int index,
        int size,
        int type,
        int stride,
        java.nio.Buffer pointer,
        int remaining
    );

    public static void glVertexAttribIPointer(
        int index,
        int size,
        int type,
        int stride,
        java.nio.Buffer pointer
    ) {
        glVertexAttribIPointerBounds(
            index,
            size,
            type,
            stride,
            pointer,
            pointer.remaining()
        );
    }

    // C function void glVertexAttribIPointer ( GLuint index, GLint size, GLenum type, GLsizei stride, GLsizei offset )

    public static native void glVertexAttribIPointer(
        int index,
        int size,
        int type,
        int stride,
        int offset
    );

    // C function void glGetVertexAttribIiv ( GLuint index, GLenum pname, GLint *params )

    public static native void glGetVertexAttribIiv(
        int index,
        int pname,
        int[] params,
        int offset
    );

    // C function void glGetVertexAttribIiv ( GLuint index, GLenum pname, GLint *params )

    public static native void glGetVertexAttribIiv(
        int index,
        int pname,
        java.nio.IntBuffer params
    );

    // C function void glGetVertexAttribIuiv ( GLuint index, GLenum pname, GLuint *params )

    public static native void glGetVertexAttribIuiv(
        int index,
        int pname,
        int[] params,
        int offset
    );

    // C function void glGetVertexAttribIuiv ( GLuint index, GLenum pname, GLuint *params )

    public static native void glGetVertexAttribIuiv(
        int index,
        int pname,
        java.nio.IntBuffer params
    );

    // C function void glVertexAttribI4i ( GLuint index, GLint x, GLint y, GLint z, GLint w )

    public static native void glVertexAttribI4i(
        int index,
        int x,
        int y,
        int z,
        int w
    );

    // C function void glVertexAttribI4ui ( GLuint index, GLuint x, GLuint y, GLuint z, GLuint w )

    public static native void glVertexAttribI4ui(
        int index,
        int x,
        int y,
        int z,
        int w
    );

    // C function void glVertexAttribI4iv ( GLuint index, const GLint *v )

    public static native void glVertexAttribI4iv(
        int index,
        int[] v,
        int offset
    );

    // C function void glVertexAttribI4iv ( GLuint index, const GLint *v )

    public static native void glVertexAttribI4iv(
        int index,
        java.nio.IntBuffer v
    );

    // C function void glVertexAttribI4uiv ( GLuint index, const GLuint *v )

    public static native void glVertexAttribI4uiv(
        int index,
        int[] v,
        int offset
    );

    // C function void glVertexAttribI4uiv ( GLuint index, const GLuint *v )

    public static native void glVertexAttribI4uiv(
        int index,
        java.nio.IntBuffer v
    );

    // C function void glGetUniformuiv ( GLuint program, GLint location, GLuint *params )

    public static native void glGetUniformuiv(
        int program,
        int location,
        int[] params,
        int offset
    );

    // C function void glGetUniformuiv ( GLuint program, GLint location, GLuint *params )

    public static native void glGetUniformuiv(
        int program,
        int location,
        java.nio.IntBuffer params
    );

    // C function GLint glGetFragDataLocation ( GLuint program, const GLchar *name )

    public static native int glGetFragDataLocation(
        int program,
        String name
    );

    // C function void glUniform1ui ( GLint location, GLuint v0 )

    public static native void glUniform1ui(
        int location,
        int v0
    );

    // C function void glUniform2ui ( GLint location, GLuint v0, GLuint v1 )

    public static native void glUniform2ui(
        int location,
        int v0,
        int v1
    );

    // C function void glUniform3ui ( GLint location, GLuint v0, GLuint v1, GLuint v2 )

    public static native void glUniform3ui(
        int location,
        int v0,
        int v1,
        int v2
    );

    // C function void glUniform4ui ( GLint location, GLuint v0, GLuint v1, GLuint v2, GLuint v3 )

    public static native void glUniform4ui(
        int location,
        int v0,
        int v1,
        int v2,
        int v3
    );

    // C function void glUniform1uiv ( GLint location, GLsizei count, const GLuint *value )

    public static native void glUniform1uiv(
        int location,
        int count,
        int[] value,
        int offset
    );

    // C function void glUniform1uiv ( GLint location, GLsizei count, const GLuint *value )

    public static native void glUniform1uiv(
        int location,
        int count,
        java.nio.IntBuffer value
    );

    // C function void glUniform2uiv ( GLint location, GLsizei count, const GLuint *value )

    public static native void glUniform2uiv(
        int location,
        int count,
        int[] value,
        int offset
    );

    // C function void glUniform2uiv ( GLint location, GLsizei count, const GLuint *value )

    public static native void glUniform2uiv(
        int location,
        int count,
        java.nio.IntBuffer value
    );

    // C function void glUniform3uiv ( GLint location, GLsizei count, const GLuint *value )

    public static native void glUniform3uiv(
        int location,
        int count,
        int[] value,
        int offset
    );

    // C function void glUniform3uiv ( GLint location, GLsizei count, const GLuint *value )

    public static native void glUniform3uiv(
        int location,
        int count,
        java.nio.IntBuffer value
    );

    // C function void glUniform4uiv ( GLint location, GLsizei count, const GLuint *value )

    public static native void glUniform4uiv(
        int location,
        int count,
        int[] value,
        int offset
    );

    // C function void glUniform4uiv ( GLint location, GLsizei count, const GLuint *value )

    public static native void glUniform4uiv(
        int location,
        int count,
        java.nio.IntBuffer value
    );

    // C function void glClearBufferiv ( GLenum buffer, GLint drawbuffer, const GLint *value )

    public static native void glClearBufferiv(
        int buffer,
        int drawbuffer,
        int[] value,
        int offset
    );

    // C function void glClearBufferiv ( GLenum buffer, GLint drawbuffer, const GLint *value )

    public static native void glClearBufferiv(
        int buffer,
        int drawbuffer,
        java.nio.IntBuffer value
    );

    // C function void glClearBufferuiv ( GLenum buffer, GLint drawbuffer, const GLuint *value )

    public static native void glClearBufferuiv(
        int buffer,
        int drawbuffer,
        int[] value,
        int offset
    );

    // C function void glClearBufferuiv ( GLenum buffer, GLint drawbuffer, const GLuint *value )

    public static native void glClearBufferuiv(
        int buffer,
        int drawbuffer,
        java.nio.IntBuffer value
    );

    // C function void glClearBufferfv ( GLenum buffer, GLint drawbuffer, const GLfloat *value )

    public static native void glClearBufferfv(
        int buffer,
        int drawbuffer,
        float[] value,
        int offset
    );

    // C function void glClearBufferfv ( GLenum buffer, GLint drawbuffer, const GLfloat *value )

    public static native void glClearBufferfv(
        int buffer,
        int drawbuffer,
        java.nio.FloatBuffer value
    );

    // C function void glClearBufferfi ( GLenum buffer, GLint drawbuffer, GLfloat depth, GLint stencil )

    public static native void glClearBufferfi(
        int buffer,
        int drawbuffer,
        float depth,
        int stencil
    );

    // C function const GLubyte * glGetStringi ( GLenum name, GLuint index )

    public static native String glGetStringi(
        int name,
        int index
    );

    // C function void glCopyBufferSubData ( GLenum readTarget, GLenum writeTarget, GLintptr readOffset, GLintptr writeOffset, GLsizeiptr size )

    public static native void glCopyBufferSubData(
        int readTarget,
        int writeTarget,
        int readOffset,
        int writeOffset,
        int size
    );

    // C function void glGetUniformIndices ( GLuint program, GLsizei uniformCount, const GLchar *const *uniformNames, GLuint *uniformIndices )

    public static native void glGetUniformIndices(
        int program,
        String[] uniformNames,
        int[] uniformIndices,
        int uniformIndicesOffset
    );

    // C function void glGetUniformIndices ( GLuint program, GLsizei uniformCount, const GLchar *const *uniformNames, GLuint *uniformIndices )

    public static native void glGetUniformIndices(
        int program,
        String[] uniformNames,
        java.nio.IntBuffer uniformIndices
    );

    // C function void glGetActiveUniformsiv ( GLuint program, GLsizei uniformCount, const GLuint *uniformIndices, GLenum pname, GLint *params )

    public static native void glGetActiveUniformsiv(
        int program,
        int uniformCount,
        int[] uniformIndices,
        int uniformIndicesOffset,
        int pname,
        int[] params,
        int paramsOffset
    );

    // C function void glGetActiveUniformsiv ( GLuint program, GLsizei uniformCount, const GLuint *uniformIndices, GLenum pname, GLint *params )

    public static native void glGetActiveUniformsiv(
        int program,
        int uniformCount,
        java.nio.IntBuffer uniformIndices,
        int pname,
        java.nio.IntBuffer params
    );

    // C function GLuint glGetUniformBlockIndex ( GLuint program, const GLchar *uniformBlockName )

    public static native int glGetUniformBlockIndex(
        int program,
        String uniformBlockName
    );

    // C function void glGetActiveUniformBlockiv ( GLuint program, GLuint uniformBlockIndex, GLenum pname, GLint *params )

    public static native void glGetActiveUniformBlockiv(
        int program,
        int uniformBlockIndex,
        int pname,
        int[] params,
        int offset
    );

    // C function void glGetActiveUniformBlockiv ( GLuint program, GLuint uniformBlockIndex, GLenum pname, GLint *params )

    public static native void glGetActiveUniformBlockiv(
        int program,
        int uniformBlockIndex,
        int pname,
        java.nio.IntBuffer params
    );

    // C function void glGetActiveUniformBlockName ( GLuint program, GLuint uniformBlockIndex, GLsizei bufSize, GLsizei *length, GLchar *uniformBlockName )

    public static native void glGetActiveUniformBlockName(
        int program,
        int uniformBlockIndex,
        int bufSize,
        int[] length,
        int lengthOffset,
        byte[] uniformBlockName,
        int uniformBlockNameOffset
    );

    // C function void glGetActiveUniformBlockName ( GLuint program, GLuint uniformBlockIndex, GLsizei bufSize, GLsizei *length, GLchar *uniformBlockName )

    public static native void glGetActiveUniformBlockName(
        int program,
        int uniformBlockIndex,
        java.nio.Buffer length,
        java.nio.Buffer uniformBlockName
    );

    // C function void glGetActiveUniformBlockName ( GLuint program, GLuint uniformBlockIndex, GLsizei bufSize, GLsizei *length, GLchar *uniformBlockName )

    public static native String glGetActiveUniformBlockName(
        int program,
        int uniformBlockIndex
    );

    // C function void glUniformBlockBinding ( GLuint program, GLuint uniformBlockIndex, GLuint uniformBlockBinding )

    public static native void glUniformBlockBinding(
        int program,
        int uniformBlockIndex,
        int uniformBlockBinding
    );

    // C function void glDrawArraysInstanced ( GLenum mode, GLint first, GLsizei count, GLsizei instanceCount )

    public static native void glDrawArraysInstanced(
        int mode,
        int first,
        int count,
        int instanceCount
    );

    // C function void glDrawElementsInstanced ( GLenum mode, GLsizei count, GLenum type, const GLvoid *indices, GLsizei instanceCount )

    public static native void glDrawElementsInstanced(
        int mode,
        int count,
        int type,
        java.nio.Buffer indices,
        int instanceCount
    );

    // C function void glDrawElementsInstanced ( GLenum mode, GLsizei count, GLenum type, const GLvoid *indices, GLsizei instanceCount )

    public static native void glDrawElementsInstanced(
        int mode,
        int count,
        int type,
        int indicesOffset,
        int instanceCount
    );

    // C function GLsync glFenceSync ( GLenum condition, GLbitfield flags )

    public static native long glFenceSync(
        int condition,
        int flags
    );

    // C function GLboolean glIsSync ( GLsync sync )

    public static native boolean glIsSync(
        long sync
    );

    // C function void glDeleteSync ( GLsync sync )

    public static native void glDeleteSync(
        long sync
    );

    // C function GLenum glClientWaitSync ( GLsync sync, GLbitfield flags, GLuint64 timeout )

    public static native int glClientWaitSync(
        long sync,
        int flags,
        long timeout
    );

    // C function void glWaitSync ( GLsync sync, GLbitfield flags, GLuint64 timeout )

    public static native void glWaitSync(
        long sync,
        int flags,
        long timeout
    );

    // C function void glGetInteger64v ( GLenum pname, GLint64 *params )

    public static native void glGetInteger64v(
        int pname,
        long[] params,
        int offset
    );

    // C function void glGetInteger64v ( GLenum pname, GLint64 *params )

    public static native void glGetInteger64v(
        int pname,
        java.nio.LongBuffer params
    );

    // C function void glGetSynciv ( GLsync sync, GLenum pname, GLsizei bufSize, GLsizei *length, GLint *values )

    public static native void glGetSynciv(
        long sync,
        int pname,
        int bufSize,
        int[] length,
        int lengthOffset,
        int[] values,
        int valuesOffset
    );

    // C function void glGetSynciv ( GLsync sync, GLenum pname, GLsizei bufSize, GLsizei *length, GLint *values )

    public static native void glGetSynciv(
        long sync,
        int pname,
        int bufSize,
        java.nio.IntBuffer length,
        java.nio.IntBuffer values
    );

    // C function void glGetInteger64i_v ( GLenum target, GLuint index, GLint64 *data )

    public static native void glGetInteger64i_v(
        int target,
        int index,
        long[] data,
        int offset
    );

    // C function void glGetInteger64i_v ( GLenum target, GLuint index, GLint64 *data )

    public static native void glGetInteger64i_v(
        int target,
        int index,
        java.nio.LongBuffer data
    );

    // C function void glGetBufferParameteri64v ( GLenum target, GLenum pname, GLint64 *params )

    public static native void glGetBufferParameteri64v(
        int target,
        int pname,
        long[] params,
        int offset
    );

    // C function void glGetBufferParameteri64v ( GLenum target, GLenum pname, GLint64 *params )

    public static native void glGetBufferParameteri64v(
        int target,
        int pname,
        java.nio.LongBuffer params
    );

    // C function void glGenSamplers ( GLsizei count, GLuint *samplers )

    public static native void glGenSamplers(
        int count,
        int[] samplers,
        int offset
    );

    // C function void glGenSamplers ( GLsizei count, GLuint *samplers )

    public static native void glGenSamplers(
        int count,
        java.nio.IntBuffer samplers
    );

    // C function void glDeleteSamplers ( GLsizei count, const GLuint *samplers )

    public static native void glDeleteSamplers(
        int count,
        int[] samplers,
        int offset
    );

    // C function void glDeleteSamplers ( GLsizei count, const GLuint *samplers )

    public static native void glDeleteSamplers(
        int count,
        java.nio.IntBuffer samplers
    );

    // C function GLboolean glIsSampler ( GLuint sampler )

    public static native boolean glIsSampler(
        int sampler
    );

    // C function void glBindSampler ( GLuint unit, GLuint sampler )

    public static native void glBindSampler(
        int unit,
        int sampler
    );

    // C function void glSamplerParameteri ( GLuint sampler, GLenum pname, GLint param )

    public static native void glSamplerParameteri(
        int sampler,
        int pname,
        int param
    );

    // C function void glSamplerParameteriv ( GLuint sampler, GLenum pname, const GLint *param )

    public static native void glSamplerParameteriv(
        int sampler,
        int pname,
        int[] param,
        int offset
    );

    // C function void glSamplerParameteriv ( GLuint sampler, GLenum pname, const GLint *param )

    public static native void glSamplerParameteriv(
        int sampler,
        int pname,
        java.nio.IntBuffer param
    );

    // C function void glSamplerParameterf ( GLuint sampler, GLenum pname, GLfloat param )

    public static native void glSamplerParameterf(
        int sampler,
        int pname,
        float param
    );

    // C function void glSamplerParameterfv ( GLuint sampler, GLenum pname, const GLfloat *param )

    public static native void glSamplerParameterfv(
        int sampler,
        int pname,
        float[] param,
        int offset
    );

    // C function void glSamplerParameterfv ( GLuint sampler, GLenum pname, const GLfloat *param )

    public static native void glSamplerParameterfv(
        int sampler,
        int pname,
        java.nio.FloatBuffer param
    );

    // C function void glGetSamplerParameteriv ( GLuint sampler, GLenum pname, GLint *params )

    public static native void glGetSamplerParameteriv(
        int sampler,
        int pname,
        int[] params,
        int offset
    );

    // C function void glGetSamplerParameteriv ( GLuint sampler, GLenum pname, GLint *params )

    public static native void glGetSamplerParameteriv(
        int sampler,
        int pname,
        java.nio.IntBuffer params
    );

    // C function void glGetSamplerParameterfv ( GLuint sampler, GLenum pname, GLfloat *params )

    public static native void glGetSamplerParameterfv(
        int sampler,
        int pname,
        float[] params,
        int offset
    );

    // C function void glGetSamplerParameterfv ( GLuint sampler, GLenum pname, GLfloat *params )

    public static native void glGetSamplerParameterfv(
        int sampler,
        int pname,
        java.nio.FloatBuffer params
    );

    // C function void glVertexAttribDivisor ( GLuint index, GLuint divisor )

    public static native void glVertexAttribDivisor(
        int index,
        int divisor
    );

    // C function void glBindTransformFeedback ( GLenum target, GLuint id )

    public static native void glBindTransformFeedback(
        int target,
        int id
    );

    // C function void glDeleteTransformFeedbacks ( GLsizei n, const GLuint *ids )

    public static native void glDeleteTransformFeedbacks(
        int n,
        int[] ids,
        int offset
    );

    // C function void glDeleteTransformFeedbacks ( GLsizei n, const GLuint *ids )

    public static native void glDeleteTransformFeedbacks(
        int n,
        java.nio.IntBuffer ids
    );

    // C function void glGenTransformFeedbacks ( GLsizei n, GLuint *ids )

    public static native void glGenTransformFeedbacks(
        int n,
        int[] ids,
        int offset
    );

    // C function void glGenTransformFeedbacks ( GLsizei n, GLuint *ids )

    public static native void glGenTransformFeedbacks(
        int n,
        java.nio.IntBuffer ids
    );

    // C function GLboolean glIsTransformFeedback ( GLuint id )

    public static native boolean glIsTransformFeedback(
        int id
    );

    // C function void glPauseTransformFeedback ( void )

    public static native void glPauseTransformFeedback(
    );

    // C function void glResumeTransformFeedback ( void )

    public static native void glResumeTransformFeedback(
    );

    // C function void glGetProgramBinary ( GLuint program, GLsizei bufSize, GLsizei *length, GLenum *binaryFormat, GLvoid *binary )

    public static native void glGetProgramBinary(
        int program,
        int bufSize,
        int[] length,
        int lengthOffset,
        int[] binaryFormat,
        int binaryFormatOffset,
        java.nio.Buffer binary
    );

    // C function void glGetProgramBinary ( GLuint program, GLsizei bufSize, GLsizei *length, GLenum *binaryFormat, GLvoid *binary )

    public static native void glGetProgramBinary(
        int program,
        int bufSize,
        java.nio.IntBuffer length,
        java.nio.IntBuffer binaryFormat,
        java.nio.Buffer binary
    );

    // C function void glProgramBinary ( GLuint program, GLenum binaryFormat, const GLvoid *binary, GLsizei length )

    public static native void glProgramBinary(
        int program,
        int binaryFormat,
        java.nio.Buffer binary,
        int length
    );

    // C function void glProgramParameteri ( GLuint program, GLenum pname, GLint value )

    public static native void glProgramParameteri(
        int program,
        int pname,
        int value
    );

    // C function void glInvalidateFramebuffer ( GLenum target, GLsizei numAttachments, const GLenum *attachments )

    public static native void glInvalidateFramebuffer(
        int target,
        int numAttachments,
        int[] attachments,
        int offset
    );

    // C function void glInvalidateFramebuffer ( GLenum target, GLsizei numAttachments, const GLenum *attachments )

    public static native void glInvalidateFramebuffer(
        int target,
        int numAttachments,
        java.nio.IntBuffer attachments
    );

    // C function void glInvalidateSubFramebuffer ( GLenum target, GLsizei numAttachments, const GLenum *attachments, GLint x, GLint y, GLsizei width, GLsizei height )

    public static native void glInvalidateSubFramebuffer(
        int target,
        int numAttachments,
        int[] attachments,
        int offset,
        int x,
        int y,
        int width,
        int height
    );

    // C function void glInvalidateSubFramebuffer ( GLenum target, GLsizei numAttachments, const GLenum *attachments, GLint x, GLint y, GLsizei width, GLsizei height )

    public static native void glInvalidateSubFramebuffer(
        int target,
        int numAttachments,
        java.nio.IntBuffer attachments,
        int x,
        int y,
        int width,
        int height
    );

    // C function void glTexStorage2D ( GLenum target, GLsizei levels, GLenum internalformat, GLsizei width, GLsizei height )

    public static native void glTexStorage2D(
        int target,
        int levels,
        int internalformat,
        int width,
        int height
    );

    // C function void glTexStorage3D ( GLenum target, GLsizei levels, GLenum internalformat, GLsizei width, GLsizei height, GLsizei depth )

    public static native void glTexStorage3D(
        int target,
        int levels,
        int internalformat,
        int width,
        int height,
        int depth
    );

    // C function void glGetInternalformativ ( GLenum target, GLenum internalformat, GLenum pname, GLsizei bufSize, GLint *params )

    public static native void glGetInternalformativ(
        int target,
        int internalformat,
        int pname,
        int bufSize,
        int[] params,
        int offset
    );

    // C function void glGetInternalformativ ( GLenum target, GLenum internalformat, GLenum pname, GLsizei bufSize, GLint *params )

    public static native void glGetInternalformativ(
        int target,
        int internalformat,
        int pname,
        int bufSize,
        java.nio.IntBuffer params
    );

}
