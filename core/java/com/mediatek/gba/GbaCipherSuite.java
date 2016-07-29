package com.mediatek.gba;

import java.util.Hashtable;

/**
* Represents Cipher Suite as defined in TLS 1.0 spec.,
* A.5. The CipherSuite;
* C. CipherSuite definitions.
* @see <a href="http://www.ietf.org/rfc/rfc2246.txt">TLS 1.0 spec.</a>
 * @hide
*
*/
class GbaCipherSuite {
    // cipher suite code
    private final byte[] mCipherSuiteCode;

    // cipher suite name
    private final String mCipherSuiteName;

    // hash for quick access to cipher suite by name
    private static final Hashtable<String, GbaCipherSuite> mSuiteByName;

    /**
    * TLS cipher suite codes.
    */
    static final byte[] CODE_SSL_NULL_WITH_NULL_NULL = { 0x00, 0x00 };
    static final byte[] CODE_SSL_RSA_WITH_NULL_MD5 = { 0x00, 0x01 };
    static final byte[] CODE_SSL_RSA_WITH_NULL_SHA = { 0x00, 0x02 };
    static final byte[] CODE_SSL_RSA_EXPORT_WITH_RC4_40_MD5 = { 0x00, 0x03 };
    static final byte[] CODE_SSL_RSA_WITH_RC4_128_MD5 = { 0x00, 0x04 };
    static final byte[] CODE_SSL_RSA_WITH_RC4_128_SHA = { 0x00, 0x05 };
    static final byte[] CODE_SSL_RSA_EXPORT_WITH_RC2_CBC_40_MD5 = { 0x00, 0x06 };
    // BEGIN android-removed
    // static final byte[] CODE_TLS_RSA_WITH_IDEA_CBC_SHA = { 0x00, 0x07 };
    // END android-removed
    static final byte[] CODE_SSL_RSA_EXPORT_WITH_DES40_CBC_SHA = { 0x00, 0x08 };
    static final byte[] CODE_SSL_RSA_WITH_DES_CBC_SHA = { 0x00, 0x09 };
    static final byte[] CODE_SSL_RSA_WITH_3DES_EDE_CBC_SHA = { 0x00, 0x0A };
    // BEGIN android-removed
    // static final byte[] CODE_SSL_DH_DSS_EXPORT_WITH_DES40_CBC_SHA = { 0x00, 0x0B };
    // static final byte[] CODE_SSL_DH_DSS_WITH_DES_CBC_SHA = { 0x00, 0x0C };
    // static final byte[] CODE_SSL_DH_DSS_WITH_3DES_EDE_CBC_SHA = { 0x00, 0x0D };
    // static final byte[] CODE_SSL_DH_RSA_EXPORT_WITH_DES40_CBC_SHA = { 0x00, 0x0E };
    // static final byte[] CODE_SSL_DH_RSA_WITH_DES_CBC_SHA = { 0x00, 0x0F };
    // static final byte[] CODE_SSL_DH_RSA_WITH_3DES_EDE_CBC_SHA = { 0x00, 0x10 };
    // END android-removed
    static final byte[] CODE_SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA = { 0x00, 0x11 };
    static final byte[] CODE_SSL_DHE_DSS_WITH_DES_CBC_SHA = { 0x00, 0x12 };
    static final byte[] CODE_SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA = { 0x00, 0x13 };
    static final byte[] CODE_SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA = { 0x00, 0x14 };
    static final byte[] CODE_SSL_DHE_RSA_WITH_DES_CBC_SHA = { 0x00, 0x15 };
    static final byte[] CODE_SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA = { 0x00, 0x16 };
    static final byte[] CODE_SSL_DH_anon_EXPORT_WITH_RC4_40_MD5 = { 0x00, 0x17 };
    static final byte[] CODE_SSL_DH_anon_WITH_RC4_128_MD5 = { 0x00, 0x18 };
    static final byte[] CODE_SSL_DH_anon_EXPORT_WITH_DES40_CBC_SHA = { 0x00, 0x19 };
    static final byte[] CODE_SSL_DH_anon_WITH_DES_CBC_SHA = { 0x00, 0x1A };
    static final byte[] CODE_SSL_DH_anon_WITH_3DES_EDE_CBC_SHA = { 0x00, 0x1B };

    // AES Cipher Suites from RFC 3268 - http://www.ietf.org/rfc/rfc3268.txt
    static final byte[] CODE_TLS_RSA_WITH_AES_128_CBC_SHA = { 0x00, 0x2F };
    //static final byte[] CODE_TLS_DH_DSS_WITH_AES_128_CBC_SHA = { 0x00, 0x30 };
    //static final byte[] CODE_TLS_DH_RSA_WITH_AES_128_CBC_SHA = { 0x00, 0x31 };
    static final byte[] CODE_TLS_DHE_DSS_WITH_AES_128_CBC_SHA = { 0x00, 0x32 };
    static final byte[] CODE_TLS_DHE_RSA_WITH_AES_128_CBC_SHA = { 0x00, 0x33 };
    static final byte[] CODE_TLS_DH_anon_WITH_AES_128_CBC_SHA = { 0x00, 0x34 };
    static final byte[] CODE_TLS_RSA_WITH_AES_256_CBC_SHA = { 0x00, 0x35 };
    //static final byte[] CODE_TLS_DH_DSS_WITH_AES_256_CBC_SHA = { 0x00, 0x36 };
    //static final byte[] CODE_TLS_DH_RSA_WITH_AES_256_CBC_SHA = { 0x00, 0x37 };
    static final byte[] CODE_TLS_DHE_DSS_WITH_AES_256_CBC_SHA = { 0x00, 0x38 };
    static final byte[] CODE_TLS_DHE_RSA_WITH_AES_256_CBC_SHA = { 0x00, 0x39 };
    static final byte[] CODE_TLS_DH_anon_WITH_AES_256_CBC_SHA = { 0x00, 0x3A };

    // EC Cipher Suites from RFC 4492 - http://www.ietf.org/rfc/rfc4492.txt
    static final byte[] CODE_TLS_ECDH_ECDSA_WITH_NULL_SHA = { (byte) 0xc0, 0x01};
    static final byte[] CODE_TLS_ECDH_ECDSA_WITH_RC4_128_SHA = { (byte) 0xc0, 0x02};
    static final byte[] CODE_TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA = { (byte) 0xc0, 0x03};
    static final byte[] CODE_TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA = { (byte) 0xc0, 0x04};
    static final byte[] CODE_TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA = { (byte) 0xc0, 0x05};
    static final byte[] CODE_TLS_ECDHE_ECDSA_WITH_NULL_SHA = { (byte) 0xc0, 0x06};
    static final byte[] CODE_TLS_ECDHE_ECDSA_WITH_RC4_128_SHA = { (byte) 0xc0, 0x07};
    static final byte[] CODE_TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA = { (byte) 0xc0, 0x08};
    static final byte[] CODE_TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA = { (byte) 0xc0, 0x09};
    static final byte[] CODE_TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA = { (byte) 0xc0, 0x0A};
    static final byte[] CODE_TLS_ECDH_RSA_WITH_NULL_SHA = { (byte) 0xc0, 0x0B};
    static final byte[] CODE_TLS_ECDH_RSA_WITH_RC4_128_SHA = { (byte) 0xc0, 0x0C};
    static final byte[] CODE_TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA = { (byte) 0xc0, 0x0D};
    static final byte[] CODE_TLS_ECDH_RSA_WITH_AES_128_CBC_SHA = { (byte) 0xc0, 0x0E};
    static final byte[] CODE_TLS_ECDH_RSA_WITH_AES_256_CBC_SHA = { (byte) 0xc0, 0x0F};
    static final byte[] CODE_TLS_ECDHE_RSA_WITH_NULL_SHA = { (byte) 0xc0, 0x10};
    static final byte[] CODE_TLS_ECDHE_RSA_WITH_RC4_128_SHA = { (byte) 0xc0, 0x11};
    static final byte[] CODE_TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA = { (byte) 0xc0, 0x12};
    static final byte[] CODE_TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA = { (byte) 0xc0, 0x13};
    static final byte[] CODE_TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA = { (byte) 0xc0, 0x14};
    static final byte[] CODE_TLS_ECDH_anon_WITH_NULL_SHA = { (byte) 0xc0, 0x15};
    static final byte[] CODE_TLS_ECDH_anon_WITH_RC4_128_SHA = { (byte) 0xc0, 0x16};
    static final byte[] CODE_TLS_ECDH_anon_WITH_3DES_EDE_CBC_SHA = { (byte) 0xc0, 0x17};
    static final byte[] CODE_TLS_ECDH_anon_WITH_AES_128_CBC_SHA = { (byte) 0xc0, 0x18};
    static final byte[] CODE_TLS_ECDH_anon_WITH_AES_256_CBC_SHA = { (byte) 0xc0, 0x19};

    static final  GbaCipherSuite SSL_NULL_WITH_NULL_NULL = new GbaCipherSuite(
        "SSL_NULL_WITH_NULL_NULL", CODE_SSL_NULL_WITH_NULL_NULL);

    static final  GbaCipherSuite SSL_RSA_WITH_NULL_MD5 = new GbaCipherSuite(
        "SSL_RSA_WITH_NULL_MD5", CODE_SSL_RSA_WITH_NULL_MD5);

    static final  GbaCipherSuite SSL_RSA_WITH_NULL_SHA = new GbaCipherSuite(
        "SSL_RSA_WITH_NULL_SHA", CODE_SSL_RSA_WITH_NULL_SHA);

    static final  GbaCipherSuite SSL_RSA_EXPORT_WITH_RC4_40_MD5 = new GbaCipherSuite(
        "SSL_RSA_EXPORT_WITH_RC4_40_MD5", CODE_SSL_RSA_EXPORT_WITH_RC4_40_MD5);

    static final  GbaCipherSuite SSL_RSA_WITH_RC4_128_MD5 = new GbaCipherSuite(
        "SSL_RSA_WITH_RC4_128_MD5", CODE_SSL_RSA_WITH_RC4_128_MD5);

    static final  GbaCipherSuite SSL_RSA_WITH_RC4_128_SHA = new GbaCipherSuite(
        "SSL_RSA_WITH_RC4_128_SHA", CODE_SSL_RSA_WITH_RC4_128_SHA);

    static final  GbaCipherSuite SSL_RSA_EXPORT_WITH_RC2_CBC_40_MD5 = new GbaCipherSuite(
        "SSL_RSA_EXPORT_WITH_RC2_CBC_40_MD5", CODE_SSL_RSA_EXPORT_WITH_RC2_CBC_40_MD5);

    // BEGIN android-removed
    // static final  GbaCipherSuite TLS_RSA_WITH_IDEA_CBC_SHA = new GbaCipherSuite(
    //         "TLS_RSA_WITH_IDEA_CBC_SHA", CODE_TLS_RSA_WITH_IDEA_CBC_SHA);
    // END android-removed

    static final  GbaCipherSuite SSL_RSA_EXPORT_WITH_DES40_CBC_SHA = new GbaCipherSuite(
        "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA", CODE_SSL_RSA_EXPORT_WITH_DES40_CBC_SHA);

    static final  GbaCipherSuite SSL_RSA_WITH_DES_CBC_SHA = new GbaCipherSuite(
        "SSL_RSA_WITH_DES_CBC_SHA", CODE_SSL_RSA_WITH_DES_CBC_SHA);

    static final  GbaCipherSuite SSL_RSA_WITH_3DES_EDE_CBC_SHA = new GbaCipherSuite(
        "SSL_RSA_WITH_3DES_EDE_CBC_SHA", CODE_SSL_RSA_WITH_3DES_EDE_CBC_SHA);

    // BEGIN android-removed
    // static final  GbaCipherSuite SSL_DH_DSS_EXPORT_WITH_DES40_CBC_SHA = new GbaCipherSuite(
    //         "SSL_DH_DSS_EXPORT_WITH_DES40_CBC_SHA", CODE_SSL_DH_DSS_EXPORT_WITH_DES40_CBC_SHA);
    //
    // static final  GbaCipherSuite SSL_DH_DSS_WITH_DES_CBC_SHA = new GbaCipherSuite(
    //         "SSL_DH_DSS_WITH_DES_CBC_SHA", CODE_SSL_DH_DSS_WITH_DES_CBC_SHA);
    //
    // static final  GbaCipherSuite SSL_DH_DSS_WITH_3DES_EDE_CBC_SHA = new GbaCipherSuite(
    //         "SSL_DH_DSS_WITH_3DES_EDE_CBC_SHA", CODE_SSL_DH_DSS_WITH_3DES_EDE_CBC_SHA);
    //
    // static final  GbaCipherSuite SSL_DH_RSA_EXPORT_WITH_DES40_CBC_SHA = new GbaCipherSuite(
    //         "SSL_DH_RSA_EXPORT_WITH_DES40_CBC_SHA", CODE_SSL_DH_RSA_EXPORT_WITH_DES40_CBC_SHA);
    //
    // static final  GbaCipherSuite SSL_DH_RSA_WITH_DES_CBC_SHA = new GbaCipherSuite(
    //         "SSL_DH_RSA_WITH_DES_CBC_SHA", CODE_SSL_DH_RSA_WITH_DES_CBC_SHA);
    //
    // static final  GbaCipherSuite SSL_DH_RSA_WITH_3DES_EDE_CBC_SHA = new GbaCipherSuite(
    //         "SSL_DH_RSA_WITH_3DES_EDE_CBC_SHA", CODE_SSL_DH_RSA_WITH_3DES_EDE_CBC_SHA);
    // END android-removed

    static final  GbaCipherSuite SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA = new GbaCipherSuite(
        "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA", CODE_SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA);

    static final  GbaCipherSuite SSL_DHE_DSS_WITH_DES_CBC_SHA = new GbaCipherSuite(
        "SSL_DHE_DSS_WITH_DES_CBC_SHA", CODE_SSL_DHE_DSS_WITH_DES_CBC_SHA);

    static final  GbaCipherSuite SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA = new GbaCipherSuite(
        "SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA", CODE_SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA);

    static final  GbaCipherSuite SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA = new GbaCipherSuite(
        "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA", CODE_SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA);

    static final  GbaCipherSuite SSL_DHE_RSA_WITH_DES_CBC_SHA = new GbaCipherSuite(
        "SSL_DHE_RSA_WITH_DES_CBC_SHA", CODE_SSL_DHE_RSA_WITH_DES_CBC_SHA);

    static final  GbaCipherSuite SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA = new GbaCipherSuite(
        "SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA", CODE_SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA);

    static final  GbaCipherSuite SSL_DH_anon_EXPORT_WITH_RC4_40_MD5 = new GbaCipherSuite(
        "SSL_DH_anon_EXPORT_WITH_RC4_40_MD5", CODE_SSL_DH_anon_EXPORT_WITH_RC4_40_MD5);

    static final  GbaCipherSuite SSL_DH_anon_WITH_RC4_128_MD5 = new GbaCipherSuite(
        "SSL_DH_anon_WITH_RC4_128_MD5", CODE_SSL_DH_anon_WITH_RC4_128_MD5);

    static final  GbaCipherSuite SSL_DH_anon_EXPORT_WITH_DES40_CBC_SHA = new GbaCipherSuite(
        "SSL_DH_anon_EXPORT_WITH_DES40_CBC_SHA", CODE_SSL_DH_anon_EXPORT_WITH_DES40_CBC_SHA);

    static final  GbaCipherSuite SSL_DH_anon_WITH_DES_CBC_SHA = new GbaCipherSuite(
        "SSL_DH_anon_WITH_DES_CBC_SHA", CODE_SSL_DH_anon_WITH_DES_CBC_SHA);

    static final  GbaCipherSuite SSL_DH_anon_WITH_3DES_EDE_CBC_SHA = new GbaCipherSuite(
        "SSL_DH_anon_WITH_3DES_EDE_CBC_SHA", CODE_SSL_DH_anon_WITH_3DES_EDE_CBC_SHA);

    static final  GbaCipherSuite TLS_RSA_WITH_AES_128_CBC_SHA
    = new GbaCipherSuite("TLS_RSA_WITH_AES_128_CBC_SHA",
                         CODE_TLS_RSA_WITH_AES_128_CBC_SHA);
    static final  GbaCipherSuite TLS_DHE_DSS_WITH_AES_128_CBC_SHA
    = new GbaCipherSuite("TLS_DHE_DSS_WITH_AES_128_CBC_SHA",
                         CODE_TLS_DHE_DSS_WITH_AES_128_CBC_SHA);
    static final  GbaCipherSuite TLS_DHE_RSA_WITH_AES_128_CBC_SHA
    = new GbaCipherSuite("TLS_DHE_RSA_WITH_AES_128_CBC_SHA",
                         CODE_TLS_DHE_RSA_WITH_AES_128_CBC_SHA);
    static final  GbaCipherSuite TLS_DH_anon_WITH_AES_128_CBC_SHA
    = new GbaCipherSuite("TLS_DH_anon_WITH_AES_128_CBC_SHA",
                         CODE_TLS_DH_anon_WITH_AES_128_CBC_SHA);
    static final  GbaCipherSuite TLS_RSA_WITH_AES_256_CBC_SHA
    = new GbaCipherSuite("TLS_RSA_WITH_AES_256_CBC_SHA",
                         CODE_TLS_RSA_WITH_AES_256_CBC_SHA);
    static final  GbaCipherSuite TLS_DHE_DSS_WITH_AES_256_CBC_SHA
    = new GbaCipherSuite("TLS_DHE_DSS_WITH_AES_256_CBC_SHA",
                         CODE_TLS_DHE_DSS_WITH_AES_256_CBC_SHA);
    static final  GbaCipherSuite TLS_DHE_RSA_WITH_AES_256_CBC_SHA
    = new GbaCipherSuite("TLS_DHE_RSA_WITH_AES_256_CBC_SHA",
                         CODE_TLS_DHE_RSA_WITH_AES_256_CBC_SHA);
    static final  GbaCipherSuite TLS_DH_anon_WITH_AES_256_CBC_SHA
    = new GbaCipherSuite("TLS_DH_anon_WITH_AES_256_CBC_SHA",
                         CODE_TLS_DH_anon_WITH_AES_256_CBC_SHA);

    static final  GbaCipherSuite TLS_ECDH_ECDSA_WITH_NULL_SHA
    = new GbaCipherSuite("TLS_ECDH_ECDSA_WITH_NULL_SHA",
                         CODE_TLS_ECDH_ECDSA_WITH_NULL_SHA);
    static final  GbaCipherSuite TLS_ECDH_ECDSA_WITH_RC4_128_SHA
    = new GbaCipherSuite("TLS_ECDH_ECDSA_WITH_RC4_128_SHA",
                         CODE_TLS_ECDH_ECDSA_WITH_RC4_128_SHA);
    static final  GbaCipherSuite TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA
    = new GbaCipherSuite("TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA",
                         CODE_TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA);
    static final  GbaCipherSuite TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA
    = new GbaCipherSuite("TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA",
                         CODE_TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA);
    static final  GbaCipherSuite TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA
    = new GbaCipherSuite("TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA",
                         CODE_TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA);
    static final  GbaCipherSuite TLS_ECDHE_ECDSA_WITH_NULL_SHA
    = new GbaCipherSuite("TLS_ECDHE_ECDSA_WITH_NULL_SHA",
                         CODE_TLS_ECDHE_ECDSA_WITH_NULL_SHA);
    static final  GbaCipherSuite TLS_ECDHE_ECDSA_WITH_RC4_128_SHA
    = new GbaCipherSuite("TLS_ECDHE_ECDSA_WITH_RC4_128_SHA",
                         CODE_TLS_ECDHE_ECDSA_WITH_RC4_128_SHA);
    static final  GbaCipherSuite TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA
    = new GbaCipherSuite("TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA",
                         CODE_TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA);
    static final  GbaCipherSuite TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA
    = new GbaCipherSuite("TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
                         CODE_TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA);
    static final  GbaCipherSuite TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA
    = new GbaCipherSuite("TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA",
                         CODE_TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA);
    static final  GbaCipherSuite TLS_ECDH_RSA_WITH_NULL_SHA
    = new GbaCipherSuite("TLS_ECDH_RSA_WITH_NULL_SHA",
                         CODE_TLS_ECDH_RSA_WITH_NULL_SHA);
    static final  GbaCipherSuite TLS_ECDH_RSA_WITH_RC4_128_SHA
    = new GbaCipherSuite("TLS_ECDH_RSA_WITH_RC4_128_SHA",
                         CODE_TLS_ECDH_RSA_WITH_RC4_128_SHA);
    static final  GbaCipherSuite TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA
    = new GbaCipherSuite("TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA",
                         CODE_TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA);
    static final  GbaCipherSuite TLS_ECDH_RSA_WITH_AES_128_CBC_SHA
    = new GbaCipherSuite("TLS_ECDH_RSA_WITH_AES_128_CBC_SHA",
                         CODE_TLS_ECDH_RSA_WITH_AES_128_CBC_SHA);
    static final  GbaCipherSuite TLS_ECDH_RSA_WITH_AES_256_CBC_SHA
    = new GbaCipherSuite("TLS_ECDH_RSA_WITH_AES_256_CBC_SHA",
                         CODE_TLS_ECDH_RSA_WITH_AES_256_CBC_SHA);
    static final  GbaCipherSuite TLS_ECDHE_RSA_WITH_NULL_SHA
    = new GbaCipherSuite("TLS_ECDHE_RSA_WITH_NULL_SHA",
                         CODE_TLS_ECDHE_RSA_WITH_NULL_SHA);
    static final  GbaCipherSuite TLS_ECDHE_RSA_WITH_RC4_128_SHA
    = new GbaCipherSuite("TLS_ECDHE_RSA_WITH_RC4_128_SHA",
                         CODE_TLS_ECDHE_RSA_WITH_RC4_128_SHA);
    static final  GbaCipherSuite TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA
    = new GbaCipherSuite("TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA",
                         CODE_TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA);
    static final  GbaCipherSuite TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA
    = new GbaCipherSuite("TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
                         CODE_TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA);
    static final  GbaCipherSuite TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA
    = new GbaCipherSuite("TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
                         CODE_TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA);
    static final  GbaCipherSuite TLS_ECDH_anon_WITH_NULL_SHA
    = new GbaCipherSuite("TLS_ECDH_anon_WITH_NULL_SHA",
                         CODE_TLS_ECDH_anon_WITH_NULL_SHA);
    static final  GbaCipherSuite TLS_ECDH_anon_WITH_RC4_128_SHA
    = new GbaCipherSuite("TLS_ECDH_anon_WITH_RC4_128_SHA",
                         CODE_TLS_ECDH_anon_WITH_RC4_128_SHA);
    static final  GbaCipherSuite TLS_ECDH_anon_WITH_3DES_EDE_CBC_SHA
    = new GbaCipherSuite("TLS_ECDH_anon_WITH_3DES_EDE_CBC_SHA",
                         CODE_TLS_ECDH_anon_WITH_3DES_EDE_CBC_SHA);
    static final  GbaCipherSuite TLS_ECDH_anon_WITH_AES_128_CBC_SHA
    = new GbaCipherSuite("TLS_ECDH_anon_WITH_AES_128_CBC_SHA",
                         CODE_TLS_ECDH_anon_WITH_AES_128_CBC_SHA);
    static final  GbaCipherSuite TLS_ECDH_anon_WITH_AES_256_CBC_SHA
    = new GbaCipherSuite("TLS_ECDH_anon_WITH_AES_256_CBC_SHA",
                         CODE_TLS_ECDH_anon_WITH_AES_256_CBC_SHA);

    // arrays for quick access to cipher suite by code
    private static final  GbaCipherSuite[] SUITES_BY_CODE_0x00 = {
        // http://www.iana.org/assignments/tls-parameters/tls-parameters.xml
        SSL_NULL_WITH_NULL_NULL,                          // { 0x00, 0x00 };
        SSL_RSA_WITH_NULL_MD5,                            // { 0x00, 0x01 };
        SSL_RSA_WITH_NULL_SHA,                            // { 0x00, 0x02 };
        SSL_RSA_EXPORT_WITH_RC4_40_MD5,                   // { 0x00, 0x03 };
        SSL_RSA_WITH_RC4_128_MD5,                         // { 0x00, 0x04 };
        SSL_RSA_WITH_RC4_128_SHA,                         // { 0x00, 0x05 };
        // BEGIN android-changed
        null, // SSL_RSA_EXPORT_WITH_RC2_CBC_40_MD5,      // { 0x00, 0x06 };
        null, // TLS_RSA_WITH_IDEA_CBC_SHA,               // { 0x00, 0x07 };
        // END android-changed
        SSL_RSA_EXPORT_WITH_DES40_CBC_SHA,                // { 0x00, 0x08 };
        SSL_RSA_WITH_DES_CBC_SHA,                         // { 0x00, 0x09 };
        SSL_RSA_WITH_3DES_EDE_CBC_SHA,                    // { 0x00, 0x0a };
        // BEGIN android-changed
        null, // SSL_DH_DSS_EXPORT_WITH_DES40_CBC_SHA     // { 0x00, 0x0b };
        null, // SSL_DH_DSS_WITH_DES_CBC_SHA,             // { 0x00, 0x0c };
        null, // SSL_DH_DSS_WITH_3DES_EDE_CBC_SHA,        // { 0x00, 0x0d };
        null, // SSL_DH_RSA_EXPORT_WITH_DES40_CBC_SHA,    // { 0x00, 0x0e };
        null, // SSL_DH_RSA_WITH_DES_CBC_SHA,             // { 0x00, 0x0f };
        null, // SSL_DH_RSA_WITH_3DES_EDE_CBC_SHA,        // { 0x00, 0x10 };
        // END android-changed
        SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA,            // { 0x00, 0x11 };
        SSL_DHE_DSS_WITH_DES_CBC_SHA,                     // { 0x00, 0x12 };
        SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA,                // { 0x00, 0x13 };
        SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA,            // { 0x00, 0x14 };
        SSL_DHE_RSA_WITH_DES_CBC_SHA,                     // { 0x00, 0x15 };
        SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA,                // { 0x00, 0x16 };
        SSL_DH_anon_EXPORT_WITH_RC4_40_MD5,               // { 0x00, 0x17 };
        SSL_DH_anon_WITH_RC4_128_MD5,                     // { 0x00, 0x18 };
        SSL_DH_anon_EXPORT_WITH_DES40_CBC_SHA,            // { 0x00, 0x19 };
        SSL_DH_anon_WITH_DES_CBC_SHA,                     // { 0x00, 0x1A };
        SSL_DH_anon_WITH_3DES_EDE_CBC_SHA,                // { 0x00, 0x1B };
        // BEGIN android-added
        null, // SSL_FORTEZZA_KEA_WITH_NULL_SHA           // { 0x00, 0x1C };
        null, // SSL_FORTEZZA_KEA_WITH_FORTEZZA_CBC_SHA   // { 0x00, 0x1D };
        null, // TLS_KRB5_WITH_DES_CBC_SHA                // { 0x00, 0x1E };
        null, // TLS_KRB5_WITH_3DES_EDE_CBC_SHA           // { 0x00, 0x1F };
        null, // TLS_KRB5_WITH_RC4_128_SHA                // { 0x00, 0x20 };
        null, // TLS_KRB5_WITH_IDEA_CBC_SHA               // { 0x00, 0x21 };
        null, // TLS_KRB5_WITH_DES_CBC_MD5                // { 0x00, 0x22 };
        null, // TLS_KRB5_WITH_3DES_EDE_CBC_MD5           // { 0x00, 0x23 };
        null, // TLS_KRB5_WITH_RC4_128_MD5                // { 0x00, 0x24 };
        null, // TLS_KRB5_WITH_IDEA_CBC_MD5               // { 0x00, 0x25 };
        null, // TLS_KRB5_EXPORT_WITH_DES_CBC_40_SHA      // { 0x00, 0x26 };
        null, // TLS_KRB5_EXPORT_WITH_RC2_CBC_40_SHA      // { 0x00, 0x27 };
        null, // TLS_KRB5_EXPORT_WITH_RC4_40_SHA          // { 0x00, 0x28 };
        null, // TLS_KRB5_EXPORT_WITH_DES_CBC_40_MD5      // { 0x00, 0x29 };
        null, // TLS_KRB5_EXPORT_WITH_RC2_CBC_40_MD5      // { 0x00, 0x2A };
        null, // TLS_KRB5_EXPORT_WITH_RC4_40_MD5          // { 0x00, 0x2B };
        null, // TLS_PSK_WITH_NULL_SHA                    // { 0x00, 0x2C };
        null, // TLS_DHE_PSK_WITH_NULL_SHA                // { 0x00, 0x2D };
        null, // TLS_RSA_PSK_WITH_NULL_SHA                // { 0x00, 0x2E };
        TLS_RSA_WITH_AES_128_CBC_SHA,                     // { 0x00, 0x2F };
        null, // TLS_DH_DSS_WITH_AES_128_CBC_SHA          // { 0x00, 0x30 };
        null, // TLS_DH_RSA_WITH_AES_128_CBC_SHA          // { 0x00, 0x31 };
        TLS_DHE_DSS_WITH_AES_128_CBC_SHA,                 // { 0x00, 0x32 };
        TLS_DHE_RSA_WITH_AES_128_CBC_SHA,                 // { 0x00, 0x33 };
        TLS_DH_anon_WITH_AES_128_CBC_SHA,                 // { 0x00, 0x34 };
        TLS_RSA_WITH_AES_256_CBC_SHA,                     // { 0x00, 0x35 };
        null, // TLS_DH_DSS_WITH_AES_256_CBC_SHA,         // { 0x00, 0x36 };
        null, // TLS_DH_RSA_WITH_AES_256_CBC_SHA,         // { 0x00, 0x37 };
        TLS_DHE_DSS_WITH_AES_256_CBC_SHA,                 // { 0x00, 0x38 };
        TLS_DHE_RSA_WITH_AES_256_CBC_SHA,                 // { 0x00, 0x39 };
        TLS_DH_anon_WITH_AES_256_CBC_SHA,                 // { 0x00, 0x3A };
        // END android-added
    };
    private static final  GbaCipherSuite[] SUITES_BY_CODE_0xc0 = {
        null,                                             // { 0xc0, 0x00};
        TLS_ECDH_ECDSA_WITH_NULL_SHA,                     // { 0xc0, 0x01};
        TLS_ECDH_ECDSA_WITH_RC4_128_SHA,                  // { 0xc0, 0x02};
        TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA,             // { 0xc0, 0x03};
        TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA,              // { 0xc0, 0x04};
        TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA,              // { 0xc0, 0x05};
        TLS_ECDHE_ECDSA_WITH_NULL_SHA,                    // { 0xc0, 0x06};
        TLS_ECDHE_ECDSA_WITH_RC4_128_SHA,                 // { 0xc0, 0x07};
        TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA,            // { 0xc0, 0x08};
        TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,             // { 0xc0, 0x09};
        TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,             // { 0xc0, 0x0A};
        TLS_ECDH_RSA_WITH_NULL_SHA,                       // { 0xc0, 0x0B};
        TLS_ECDH_RSA_WITH_RC4_128_SHA,                    // { 0xc0, 0x0C};
        TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA,               // { 0xc0, 0x0D};
        TLS_ECDH_RSA_WITH_AES_128_CBC_SHA,                // { 0xc0, 0x0E};
        TLS_ECDH_RSA_WITH_AES_256_CBC_SHA,                // { 0xc0, 0x0F};
        TLS_ECDHE_RSA_WITH_NULL_SHA,                      // { 0xc0, 0x10};
        TLS_ECDHE_RSA_WITH_RC4_128_SHA,                   // { 0xc0, 0x11};
        TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA,              // { 0xc0, 0x12};
        TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,               // { 0xc0, 0x13};
        TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,               // { 0xc0, 0x14};
        TLS_ECDH_anon_WITH_NULL_SHA,                      // { 0xc0, 0x15};
        TLS_ECDH_anon_WITH_RC4_128_SHA,                   // { 0xc0, 0x16};
        TLS_ECDH_anon_WITH_3DES_EDE_CBC_SHA,              // { 0xc0, 0x17};
        TLS_ECDH_anon_WITH_AES_128_CBC_SHA,               // { 0xc0, 0x18};
        TLS_ECDH_anon_WITH_AES_256_CBC_SHA,               // { 0xc0, 0x19};
        // TLS_SRP_SHA_WITH_3DES_EDE_CBC_SHA,             // { 0xc0, 0x1A};
        // TLS_SRP_SHA_RSA_WITH_3DES_EDE_CBC_SHA,         // { 0xc0, 0x1B};
        // TLS_SRP_SHA_DSS_WITH_3DES_EDE_CBC_SHA,         // { 0xc0, 0x1C};
        // TLS_SRP_SHA_WITH_AES_128_CBC_SHA,              // { 0xc0, 0x1D};
        // TLS_SRP_SHA_RSA_WITH_AES_128_CBC_SHA,          // { 0xc0, 0x1E};
        // TLS_SRP_SHA_DSS_WITH_AES_128_CBC_SHA,          // { 0xc0, 0x1F};
        // TLS_SRP_SHA_WITH_AES_256_CBC_SHA,              // { 0xc0, 0x20};
        // TLS_SRP_SHA_RSA_WITH_AES_256_CBC_SHA,          // { 0xc0, 0x21};
        // TLS_SRP_SHA_DSS_WITH_AES_256_CBC_SHA,          // { 0xc0, 0x22};
        // TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256,       // { 0xc0, 0x23};
        // TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384,       // { 0xc0, 0x24};
        // TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256,        // { 0xc0, 0x25};
        // TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA384,        // { 0xc0, 0x26};
        // TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256,         // { 0xc0, 0x27};
        // TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384,         // { 0xc0, 0x28};
        // TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256,          // { 0xc0, 0x29};
        // TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384,          // { 0xc0, 0x2A};
        // TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,       // { 0xc0, 0x2B};
        // TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,       // { 0xc0, 0x2C};
        // TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256,        // { 0xc0, 0x2D};
        // TLS_ECDH_ECDSA_WITH_AES_256_GCM_SHA384,        // { 0xc0, 0x2E};
        // TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,         // { 0xc0, 0x2F};
        // TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,         // { 0xc0, 0x30};
        // TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256,          // { 0xc0, 0x31};
        // TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384,          // { 0xc0, 0x32};
        // TLS_ECDHE_PSK_WITH_RC4_128_SHA,                // { 0xc0, 0x33};
        // TLS_ECDHE_PSK_WITH_3DES_EDE_CBC_SHA,           // { 0xc0, 0x34};
        // TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA,            // { 0xc0, 0x35};
        // TLS_ECDHE_PSK_WITH_AES_256_CBC_SHA,            // { 0xc0, 0x36};
        // TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA256,         // { 0xc0, 0x37};
        // TLS_ECDHE_PSK_WITH_AES_256_CBC_SHA384,         // { 0xc0, 0x38};
        // TLS_ECDHE_PSK_WITH_NULL_SHA,                   // { 0xc0, 0x39};
        // TLS_ECDHE_PSK_WITH_NULL_SHA256,                // { 0xc0, 0x3A};
        // TLS_ECDHE_PSK_WITH_NULL_SHA384,                // { 0xc0, 0x3B};
    };

    private GbaCipherSuite(String name, byte[] code) {
        this.mCipherSuiteName = name;
        this.mCipherSuiteCode = code;
    }

    static {
        mSuiteByName = new Hashtable<String, GbaCipherSuite>();
        registerCipherSuitesByCode(SUITES_BY_CODE_0x00);
        registerCipherSuitesByCode(SUITES_BY_CODE_0xc0);
    }

    private static int registerCipherSuitesByCode(GbaCipherSuite[] cipherSuites) {
        int count = 0;

        for (int i = 0; i < cipherSuites.length; i++) {
            if (cipherSuites[i] == SSL_NULL_WITH_NULL_NULL) {
                continue;
            }

            if (cipherSuites[i] == null) {
                continue;
            }

            mSuiteByName.put(cipherSuites[i].getName(), cipherSuites[i]);
        }

        return count;
    }

    /**
     * Returns CipherSuite by name.
     */
    public static GbaCipherSuite getByName(String name) {
        return mSuiteByName.get(name);
    }

    public String getName() {
        return mCipherSuiteName;
    }

    public byte[] getCode() {
        return mCipherSuiteCode;
    }

}