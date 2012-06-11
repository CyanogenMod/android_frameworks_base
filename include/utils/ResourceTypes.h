/*
 * Copyright (C) 2005 The Android Open Source Project
 * This code has been modified.  Portions copyright (C) 2010, T-Mobile USA, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

//
// Definitions of resource data structures.
//
#ifndef _LIBS_UTILS_RESOURCE_TYPES_H
#define _LIBS_UTILS_RESOURCE_TYPES_H

#include <utils/Asset.h>
#include <utils/ByteOrder.h>
#include <utils/Errors.h>
#include <utils/PackageRedirectionMap.h>
#include <utils/String16.h>
#include <utils/Vector.h>

#include <utils/threads.h>

#include <stdint.h>
#include <sys/types.h>

#include <android/configuration.h>

namespace android {

/** ********************************************************************
 *  PNG Extensions
 *
 *  New private chunks that may be placed in PNG images.
 *
 *********************************************************************** */

/**
 * This chunk specifies how to split an image into segments for
 * scaling.
 *
 * There are J horizontal and K vertical segments.  These segments divide
 * the image into J*K regions as follows (where J=4 and K=3):
 *
 *      F0   S0    F1     S1
 *   +-----+----+------+-------+
 * S2|  0  |  1 |  2   |   3   |
 *   +-----+----+------+-------+
 *   |     |    |      |       |
 *   |     |    |      |       |
 * F2|  4  |  5 |  6   |   7   |
 *   |     |    |      |       |
 *   |     |    |      |       |
 *   +-----+----+------+-------+
 * S3|  8  |  9 |  10  |   11  |
 *   +-----+----+------+-------+
 *
 * Each horizontal and vertical segment is considered to by either
 * stretchable (marked by the Sx labels) or fixed (marked by the Fy
 * labels), in the horizontal or vertical axis, respectively. In the
 * above example, the first is horizontal segment (F0) is fixed, the
 * next is stretchable and then they continue to alternate. Note that
 * the segment list for each axis can begin or end with a stretchable
 * or fixed segment.
 *
 * The relative sizes of the stretchy segments indicates the relative
 * amount of stretchiness of the regions bordered by the segments.  For
 * example, regions 3, 7 and 11 above will take up more horizontal space
 * than regions 1, 5 and 9 since the horizontal segment associated with
 * the first set of regions is larger than the other set of regions.  The
 * ratios of the amount of horizontal (or vertical) space taken by any
 * two stretchable slices is exactly the ratio of their corresponding
 * segment lengths.
 *
 * xDivs and yDivs point to arrays of horizontal and vertical pixel
 * indices.  The first pair of Divs (in either array) indicate the
 * starting and ending points of the first stretchable segment in that
 * axis. The next pair specifies the next stretchable segment, etc. So
 * in the above example xDiv[0] and xDiv[1] specify the horizontal
 * coordinates for the regions labeled 1, 5 and 9.  xDiv[2] and
 * xDiv[3] specify the coordinates for regions 3, 7 and 11. Note that
 * the leftmost slices always start at x=0 and the rightmost slices
 * always end at the end of the image. So, for example, the regions 0,
 * 4 and 8 (which are fixed along the X axis) start at x value 0 and
 * go to xDiv[0] and slices 2, 6 and 10 start at xDiv[1] and end at
 * xDiv[2].
 *
 * The array pointed to by the colors field lists contains hints for
 * each of the regions.  They are ordered according left-to-right and
 * top-to-bottom as indicated above. For each segment that is a solid
 * color the array entry will contain that color value; otherwise it
 * will contain NO_COLOR.  Segments that are completely transparent
 * will always have the value TRANSPARENT_COLOR.
 *
 * The PNG chunk type is "npTc".
 */
struct Res_png_9patch
{
    Res_png_9patch() : wasDeserialized(false), xDivs(NULL),
                       yDivs(NULL), colors(NULL) { }

    int8_t wasDeserialized;
    int8_t numXDivs;
    int8_t numYDivs;
    int8_t numColors;

    // These tell where the next section of a patch starts.
    // For example, the first patch includes the pixels from
    // 0 to xDivs[0]-1 and the second patch includes the pixels
    // from xDivs[0] to xDivs[1]-1.
    // Note: allocation/free of these pointers is left to the caller.
    int32_t* xDivs;
    int32_t* yDivs;

    int32_t paddingLeft, paddingRight;
    int32_t paddingTop, paddingBottom;

    enum {
        // The 9 patch segment is not a solid color.
        NO_COLOR = 0x00000001,

        // The 9 patch segment is completely transparent.
        TRANSPARENT_COLOR = 0x00000000
    };
    // Note: allocation/free of this pointer is left to the caller.
    uint32_t* colors;

    // Convert data from device representation to PNG file representation.
    void deviceToFile();
    // Convert data from PNG file representation to device representation.
    void fileToDevice();
    // Serialize/Marshall the patch data into a newly malloc-ed block
    void* serialize();
    // Serialize/Marshall the patch data
    void serialize(void* outData);
    // Deserialize/Unmarshall the patch data
    static Res_png_9patch* deserialize(const void* data);
    // Compute the size of the serialized data structure
    size_t serializedSize();
};

/** ********************************************************************
 *  Base Types
 *
 *  These are standard types that are shared between multiple specific
 *  resource types.
 *
 *********************************************************************** */

/**
 * Header that appears at the front of every data chunk in a resource.
 */
struct ResChunk_header
{
    // Type identifier for this chunk.  The meaning of this value depends
    // on the containing chunk.
    uint16_t type;

    // Size of the chunk header (in bytes).  Adding this value to
    // the address of the chunk allows you to find its associated data
    // (if any).
    uint16_t headerSize;

    // Total size of this chunk (in bytes).  This is the chunkSize plus
    // the size of any data associated with the chunk.  Adding this value
    // to the chunk allows you to completely skip its contents (including
    // any child chunks).  If this value is the same as chunkSize, there is
    // no data associated with the chunk.
    uint32_t size;
};

enum {
    RES_NULL_TYPE               = 0x0000,
    RES_STRING_POOL_TYPE        = 0x0001,
    RES_TABLE_TYPE              = 0x0002,
    RES_XML_TYPE                = 0x0003,

    // Chunk types in RES_XML_TYPE
    RES_XML_FIRST_CHUNK_TYPE    = 0x0100,
    RES_XML_START_NAMESPACE_TYPE= 0x0100,
    RES_XML_END_NAMESPACE_TYPE  = 0x0101,
    RES_XML_START_ELEMENT_TYPE  = 0x0102,
    RES_XML_END_ELEMENT_TYPE    = 0x0103,
    RES_XML_CDATA_TYPE          = 0x0104,
    RES_XML_LAST_CHUNK_TYPE     = 0x017f,
    // This contains a uint32_t array mapping strings in the string
    // pool back to resource identifiers.  It is optional.
    RES_XML_RESOURCE_MAP_TYPE   = 0x0180,

    // Chunk types in RES_TABLE_TYPE
    RES_TABLE_PACKAGE_TYPE      = 0x0200,
    RES_TABLE_TYPE_TYPE         = 0x0201,
    RES_TABLE_TYPE_SPEC_TYPE    = 0x0202
};

/**
 * Macros for building/splitting resource identifiers.
 */
#define Res_VALIDID(resid) (resid != 0)
#define Res_CHECKID(resid) ((resid&0xFFFF0000) != 0)
#define Res_MAKEID(package, type, entry) \
    (((package+1)<<24) | (((type+1)&0xFF)<<16) | (entry&0xFFFF))
#define Res_GETPACKAGE(id) ((id>>24)-1)
#define Res_GETTYPE(id) (((id>>16)&0xFF)-1)
#define Res_GETENTRY(id) (id&0xFFFF)

#define Res_INTERNALID(resid) ((resid&0xFFFF0000) != 0 && (resid&0xFF0000) == 0)
#define Res_MAKEINTERNAL(entry) (0x01000000 | (entry&0xFFFF))
#define Res_MAKEARRAY(entry) (0x02000000 | (entry&0xFFFF))

#define Res_MAXPACKAGE 255

/**
 * Representation of a value in a resource, supplying type
 * information.
 */
struct Res_value
{
    // Number of bytes in this structure.
    uint16_t size;

    // Always set to 0.
    uint8_t res0;
        
    // Type of the data value.
    enum {
        // Contains no data.
        TYPE_NULL = 0x00,
        // The 'data' holds a ResTable_ref, a reference to another resource
        // table entry.
        TYPE_REFERENCE = 0x01,
        // The 'data' holds an attribute resource identifier.
        TYPE_ATTRIBUTE = 0x02,
        // The 'data' holds an index into the containing resource table's
        // global value string pool.
        TYPE_STRING = 0x03,
        // The 'data' holds a single-precision floating point number.
        TYPE_FLOAT = 0x04,
        // The 'data' holds a complex number encoding a dimension value,
        // such as "100in".
        TYPE_DIMENSION = 0x05,
        // The 'data' holds a complex number encoding a fraction of a
        // container.
        TYPE_FRACTION = 0x06,

        // Beginning of integer flavors...
        TYPE_FIRST_INT = 0x10,

        // The 'data' is a raw integer value of the form n..n.
        TYPE_INT_DEC = 0x10,
        // The 'data' is a raw integer value of the form 0xn..n.
        TYPE_INT_HEX = 0x11,
        // The 'data' is either 0 or 1, for input "false" or "true" respectively.
        TYPE_INT_BOOLEAN = 0x12,

        // Beginning of color integer flavors...
        TYPE_FIRST_COLOR_INT = 0x1c,

        // The 'data' is a raw integer value of the form #aarrggbb.
        TYPE_INT_COLOR_ARGB8 = 0x1c,
        // The 'data' is a raw integer value of the form #rrggbb.
        TYPE_INT_COLOR_RGB8 = 0x1d,
        // The 'data' is a raw integer value of the form #argb.
        TYPE_INT_COLOR_ARGB4 = 0x1e,
        // The 'data' is a raw integer value of the form #rgb.
        TYPE_INT_COLOR_RGB4 = 0x1f,

        // ...end of integer flavors.
        TYPE_LAST_COLOR_INT = 0x1f,

        // ...end of integer flavors.
        TYPE_LAST_INT = 0x1f
    };
    uint8_t dataType;

    // Structure of complex data values (TYPE_UNIT and TYPE_FRACTION)
    enum {
        // Where the unit type information is.  This gives us 16 possible
        // types, as defined below.
        COMPLEX_UNIT_SHIFT = 0,
        COMPLEX_UNIT_MASK = 0xf,

        // TYPE_DIMENSION: Value is raw pixels.
        COMPLEX_UNIT_PX = 0,
        // TYPE_DIMENSION: Value is Device Independent Pixels.
        COMPLEX_UNIT_DIP = 1,
        // TYPE_DIMENSION: Value is a Scaled device independent Pixels.
        COMPLEX_UNIT_SP = 2,
        // TYPE_DIMENSION: Value is in points.
        COMPLEX_UNIT_PT = 3,
        // TYPE_DIMENSION: Value is in inches.
        COMPLEX_UNIT_IN = 4,
        // TYPE_DIMENSION: Value is in millimeters.
        COMPLEX_UNIT_MM = 5,

        // TYPE_FRACTION: A basic fraction of the overall size.
        COMPLEX_UNIT_FRACTION = 0,
        // TYPE_FRACTION: A fraction of the parent size.
        COMPLEX_UNIT_FRACTION_PARENT = 1,

        // Where the radix information is, telling where the decimal place
        // appears in the mantissa.  This give us 4 possible fixed point
        // representations as defined below.
        COMPLEX_RADIX_SHIFT = 4,
        COMPLEX_RADIX_MASK = 0x3,

        // The mantissa is an integral number -- i.e., 0xnnnnnn.0
        COMPLEX_RADIX_23p0 = 0,
        // The mantissa magnitude is 16 bits -- i.e, 0xnnnn.nn
        COMPLEX_RADIX_16p7 = 1,
        // The mantissa magnitude is 8 bits -- i.e, 0xnn.nnnn
        COMPLEX_RADIX_8p15 = 2,
        // The mantissa magnitude is 0 bits -- i.e, 0x0.nnnnnn
        COMPLEX_RADIX_0p23 = 3,

        // Where the actual value is.  This gives us 23 bits of
        // precision.  The top bit is the sign.
        COMPLEX_MANTISSA_SHIFT = 8,
        COMPLEX_MANTISSA_MASK = 0xffffff
    };

    // The data for this item, as interpreted according to dataType.
    uint32_t data;

    void copyFrom_dtoh(const Res_value& src);
};

/**
 *  This is a reference to a unique entry (a ResTable_entry structure)
 *  in a resource table.  The value is structured as: 0xpptteeee,
 *  where pp is the package index, tt is the type index in that
 *  package, and eeee is the entry index in that type.  The package
 *  and type values start at 1 for the first item, to help catch cases
 *  where they have not been supplied.
 */
struct ResTable_ref
{
    uint32_t ident;
};

/**
 * Reference to a string in a string pool.
 */
struct ResStringPool_ref
{
    // Index into the string pool table (uint32_t-offset from the indices
    // immediately after ResStringPool_header) at which to find the location
    // of the string data in the pool.
    uint32_t index;
};

/** ********************************************************************
 *  String Pool
 *
 *  A set of strings that can be references by others through a
 *  ResStringPool_ref.
 *
 *********************************************************************** */

/**
 * Definition for a pool of strings.  The data of this chunk is an
 * array of uint32_t providing indices into the pool, relative to
 * stringsStart.  At stringsStart are all of the UTF-16 strings
 * concatenated together; each starts with a uint16_t of the string's
 * length and each ends with a 0x0000 terminator.  If a string is >
 * 32767 characters, the high bit of the length is set meaning to take
 * those 15 bits as a high word and it will be followed by another
 * uint16_t containing the low word.
 *
 * If styleCount is not zero, then immediately following the array of
 * uint32_t indices into the string table is another array of indices
 * into a style table starting at stylesStart.  Each entry in the
 * style table is an array of ResStringPool_span structures.
 */
struct ResStringPool_header
{
    struct ResChunk_header header;

    // Number of strings in this pool (number of uint32_t indices that follow
    // in the data).
    uint32_t stringCount;

    // Number of style span arrays in the pool (number of uint32_t indices
    // follow the string indices).
    uint32_t styleCount;

    // Flags.
    enum {
        // If set, the string index is sorted by the string values (based
        // on strcmp16()).
        SORTED_FLAG = 1<<0,

        // String pool is encoded in UTF-8
        UTF8_FLAG = 1<<8
    };
    uint32_t flags;

    // Index from header of the string data.
    uint32_t stringsStart;

    // Index from header of the style data.
    uint32_t stylesStart;
};

/**
 * This structure defines a span of style information associated with
 * a string in the pool.
 */
struct ResStringPool_span
{
    enum {
        END = 0xFFFFFFFF
    };

    // This is the name of the span -- that is, the name of the XML
    // tag that defined it.  The special value END (0xFFFFFFFF) indicates
    // the end of an array of spans.
    ResStringPool_ref name;

    // The range of characters in the string that this span applies to.
    uint32_t firstChar, lastChar;
};

/**
 * Convenience class for accessing data in a ResStringPool resource.
 */
class ResStringPool
{
public:
    ResStringPool();
    ResStringPool(const void* data, size_t size, bool copyData=false);
    ~ResStringPool();

    status_t setTo(const void* data, size_t size, bool copyData=false);

    status_t getError() const;

    void uninit();

    inline const char16_t* stringAt(const ResStringPool_ref& ref, size_t* outLen) const {
        return stringAt(ref.index, outLen);
    }
    const char16_t* stringAt(size_t idx, size_t* outLen) const;

    const char* string8At(size_t idx, size_t* outLen) const;

    const ResStringPool_span* styleAt(const ResStringPool_ref& ref) const;
    const ResStringPool_span* styleAt(size_t idx) const;

    ssize_t indexOfString(const char16_t* str, size_t strLen) const;

    size_t size() const;

#ifndef HAVE_ANDROID_OS
    bool isUTF8() const;
#endif

private:
    status_t                    mError;
    void*                       mOwnedData;
    const ResStringPool_header* mHeader;
    size_t                      mSize;
    mutable Mutex               mDecodeLock;
    const uint32_t*             mEntries;
    const uint32_t*             mEntryStyles;
    const void*                 mStrings;
    char16_t**                  mCache;
    uint32_t                    mStringPoolSize;    // number of uint16_t
    const uint32_t*             mStyles;
    uint32_t                    mStylePoolSize;    // number of uint32_t
};

/** ********************************************************************
 *  XML Tree
 *
 *  Binary representation of an XML document.  This is designed to
 *  express everything in an XML document, in a form that is much
 *  easier to parse on the device.
 *
 *********************************************************************** */

/**
 * XML tree header.  This appears at the front of an XML tree,
 * describing its content.  It is followed by a flat array of
 * ResXMLTree_node structures; the hierarchy of the XML document
 * is described by the occurrance of RES_XML_START_ELEMENT_TYPE
 * and corresponding RES_XML_END_ELEMENT_TYPE nodes in the array.
 */
struct ResXMLTree_header
{
    struct ResChunk_header header;
};

/**
 * Basic XML tree node.  A single item in the XML document.  Extended info
 * about the node can be found after header.headerSize.
 */
struct ResXMLTree_node
{
    struct ResChunk_header header;

    // Line number in original source file at which this element appeared.
    uint32_t lineNumber;

    // Optional XML comment that was associated with this element; -1 if none.
    struct ResStringPool_ref comment;
};

/**
 * Extended XML tree node for CDATA tags -- includes the CDATA string.
 * Appears header.headerSize bytes after a ResXMLTree_node.
 */
struct ResXMLTree_cdataExt
{
    // The raw CDATA character data.
    struct ResStringPool_ref data;
    
    // The typed value of the character data if this is a CDATA node.
    struct Res_value typedData;
};

/**
 * Extended XML tree node for namespace start/end nodes.
 * Appears header.headerSize bytes after a ResXMLTree_node.
 */
struct ResXMLTree_namespaceExt
{
    // The prefix of the namespace.
    struct ResStringPool_ref prefix;
    
    // The URI of the namespace.
    struct ResStringPool_ref uri;
};

/**
 * Extended XML tree node for element start/end nodes.
 * Appears header.headerSize bytes after a ResXMLTree_node.
 */
struct ResXMLTree_endElementExt
{
    // String of the full namespace of this element.
    struct ResStringPool_ref ns;
    
    // String name of this node if it is an ELEMENT; the raw
    // character data if this is a CDATA node.
    struct ResStringPool_ref name;
};

/**
 * Extended XML tree node for start tags -- includes attribute
 * information.
 * Appears header.headerSize bytes after a ResXMLTree_node.
 */
struct ResXMLTree_attrExt
{
    // String of the full namespace of this element.
    struct ResStringPool_ref ns;
    
    // String name of this node if it is an ELEMENT; the raw
    // character data if this is a CDATA node.
    struct ResStringPool_ref name;
    
    // Byte offset from the start of this structure where the attributes start.
    uint16_t attributeStart;
    
    // Size of the ResXMLTree_attribute structures that follow.
    uint16_t attributeSize;
    
    // Number of attributes associated with an ELEMENT.  These are
    // available as an array of ResXMLTree_attribute structures
    // immediately following this node.
    uint16_t attributeCount;
    
    // Index (1-based) of the "id" attribute. 0 if none.
    uint16_t idIndex;
    
    // Index (1-based) of the "class" attribute. 0 if none.
    uint16_t classIndex;
    
    // Index (1-based) of the "style" attribute. 0 if none.
    uint16_t styleIndex;
};

struct ResXMLTree_attribute
{
    // Namespace of this attribute.
    struct ResStringPool_ref ns;
    
    // Name of this attribute.
    struct ResStringPool_ref name;

    // The original raw string value of this attribute.
    struct ResStringPool_ref rawValue;
    
    // Processesd typed value of this attribute.
    struct Res_value typedValue;
};

class ResXMLTree;

class ResXMLParser
{
public:
    ResXMLParser(const ResXMLTree& tree);

    enum event_code_t {
        BAD_DOCUMENT = -1,
        START_DOCUMENT = 0,
        END_DOCUMENT = 1,
        
        FIRST_CHUNK_CODE = RES_XML_FIRST_CHUNK_TYPE, 
        
        START_NAMESPACE = RES_XML_START_NAMESPACE_TYPE,
        END_NAMESPACE = RES_XML_END_NAMESPACE_TYPE,
        START_TAG = RES_XML_START_ELEMENT_TYPE,
        END_TAG = RES_XML_END_ELEMENT_TYPE,
        TEXT = RES_XML_CDATA_TYPE
    };

    struct ResXMLPosition
    {
        event_code_t                eventCode;
        const ResXMLTree_node*      curNode;
        const void*                 curExt;
    };

    void restart();

    const ResStringPool& getStrings() const;

    event_code_t getEventType() const;
    // Note, unlike XmlPullParser, the first call to next() will return
    // START_TAG of the first element.
    event_code_t next();

    // These are available for all nodes:
    int32_t getCommentID() const;
    const char16_t* getComment(size_t* outLen) const;
    uint32_t getLineNumber() const;
    
    // This is available for TEXT:
    int32_t getTextID() const;
    const char16_t* getText(size_t* outLen) const;
    ssize_t getTextValue(Res_value* outValue) const;
    
    // These are available for START_NAMESPACE and END_NAMESPACE:
    int32_t getNamespacePrefixID() const;
    const char16_t* getNamespacePrefix(size_t* outLen) const;
    int32_t getNamespaceUriID() const;
    const char16_t* getNamespaceUri(size_t* outLen) const;
    
    // These are available for START_TAG and END_TAG:
    int32_t getElementNamespaceID() const;
    const char16_t* getElementNamespace(size_t* outLen) const;
    int32_t getElementNameID() const;
    const char16_t* getElementName(size_t* outLen) const;
    
    // Remaining methods are for retrieving information about attributes
    // associated with a START_TAG:
    
    size_t getAttributeCount() const;
    
    // Returns -1 if no namespace, -2 if idx out of range.
    int32_t getAttributeNamespaceID(size_t idx) const;
    const char16_t* getAttributeNamespace(size_t idx, size_t* outLen) const;
    
    int32_t getAttributeNameID(size_t idx) const;
    const char16_t* getAttributeName(size_t idx, size_t* outLen) const;
    uint32_t getAttributeNameResID(size_t idx) const;
    
    int32_t getAttributeValueStringID(size_t idx) const;
    const char16_t* getAttributeStringValue(size_t idx, size_t* outLen) const;
    
    int32_t getAttributeDataType(size_t idx) const;
    int32_t getAttributeData(size_t idx) const;
    ssize_t getAttributeValue(size_t idx, Res_value* outValue) const;

    ssize_t indexOfAttribute(const char* ns, const char* attr) const;
    ssize_t indexOfAttribute(const char16_t* ns, size_t nsLen,
                             const char16_t* attr, size_t attrLen) const;

    ssize_t indexOfID() const;
    ssize_t indexOfClass() const;
    ssize_t indexOfStyle() const;

    void getPosition(ResXMLPosition* pos) const;
    void setPosition(const ResXMLPosition& pos);

private:
    friend class ResXMLTree;
    
    event_code_t nextNode();

    const ResXMLTree&           mTree;
    event_code_t                mEventCode;
    const ResXMLTree_node*      mCurNode;
    const void*                 mCurExt;
};

/**
 * Convenience class for accessing data in a ResXMLTree resource.
 */
class ResXMLTree : public ResXMLParser
{
public:
    ResXMLTree();
    ResXMLTree(const void* data, size_t size, bool copyData=false);
    ~ResXMLTree();

    status_t setTo(const void* data, size_t size, bool copyData=false);

    status_t getError() const;

    void uninit();

private:
    friend class ResXMLParser;

    status_t validateNode(const ResXMLTree_node* node) const;

    status_t                    mError;
    void*                       mOwnedData;
    const ResXMLTree_header*    mHeader;
    size_t                      mSize;
    const uint8_t*              mDataEnd;
    ResStringPool               mStrings;
    const uint32_t*             mResIds;
    size_t                      mNumResIds;
    const ResXMLTree_node*      mRootNode;
    const void*                 mRootExt;
    event_code_t                mRootCode;
};

/** ********************************************************************
 *  RESOURCE TABLE
 *
 *********************************************************************** */

/**
 * Header for a resource table.  Its data contains a series of
 * additional chunks:
 *   * A ResStringPool_header containing all table values.
 *   * One or more ResTable_package chunks.
 *
 * Specific entries within a resource table can be uniquely identified
 * with a single integer as defined by the ResTable_ref structure.
 */
struct ResTable_header
{
    struct ResChunk_header header;

    // The number of ResTable_package structures.
    uint32_t packageCount;
};

/**
 * A collection of resource data types within a package.  Followed by
 * one or more ResTable_type and ResTable_typeSpec structures containing the
 * entry values for each resource type.
 */
struct ResTable_package
{
    struct ResChunk_header header;

    // If this is a base package, its ID.  Package IDs start
    // at 1 (corresponding to the value of the package bits in a
    // resource identifier).  0 means this is not a base package.
    uint32_t id;

    // Actual name of this package, \0-terminated.
    char16_t name[128];

    // Offset to a ResStringPool_header defining the resource
    // type symbol table.  If zero, this package is inheriting from
    // another base package (overriding specific values in it).
    uint32_t typeStrings;

    // Last index into typeStrings that is for public use by others.
    uint32_t lastPublicType;

    // Offset to a ResStringPool_header defining the resource
    // key symbol table.  If zero, this package is inheriting from
    // another base package (overriding specific values in it).
    uint32_t keyStrings;

    // Last index into keyStrings that is for public use by others.
    uint32_t lastPublicKey;
};

/**
 * Describes a particular resource configuration.
 */
struct ResTable_config
{
    // Number of bytes in this structure.
    uint32_t size;
    
    union {
        struct {
            // Mobile country code (from SIM).  0 means "any".
            uint16_t mcc;
            // Mobile network code (from SIM).  0 means "any".
            uint16_t mnc;
        };
        uint32_t imsi;
    };
    
    union {
        struct {
            // \0\0 means "any".  Otherwise, en, fr, etc.
            char language[2];
            
            // \0\0 means "any".  Otherwise, US, CA, etc.
            char country[2];
        };
        uint32_t locale;
    };
    
    enum {
        ORIENTATION_ANY  = ACONFIGURATION_ORIENTATION_ANY,
        ORIENTATION_PORT = ACONFIGURATION_ORIENTATION_PORT,
        ORIENTATION_LAND = ACONFIGURATION_ORIENTATION_LAND,
        ORIENTATION_SQUARE = ACONFIGURATION_ORIENTATION_SQUARE,
    };
    
    enum {
        TOUCHSCREEN_ANY  = ACONFIGURATION_TOUCHSCREEN_ANY,
        TOUCHSCREEN_NOTOUCH  = ACONFIGURATION_TOUCHSCREEN_NOTOUCH,
        TOUCHSCREEN_STYLUS  = ACONFIGURATION_TOUCHSCREEN_STYLUS,
        TOUCHSCREEN_FINGER  = ACONFIGURATION_TOUCHSCREEN_FINGER,
    };
    
    enum {
        DENSITY_DEFAULT = ACONFIGURATION_DENSITY_DEFAULT,
        DENSITY_LOW = ACONFIGURATION_DENSITY_LOW,
        DENSITY_MEDIUM = ACONFIGURATION_DENSITY_MEDIUM,
        DENSITY_TV = ACONFIGURATION_DENSITY_TV,
        DENSITY_HIGH = ACONFIGURATION_DENSITY_HIGH,
        DENSITY_NONE = ACONFIGURATION_DENSITY_NONE
    };
    
    union {
        struct {
            uint8_t orientation;
            uint8_t touchscreen;
            uint16_t density;
        };
        uint32_t screenType;
    };
    
    enum {
        KEYBOARD_ANY  = ACONFIGURATION_KEYBOARD_ANY,
        KEYBOARD_NOKEYS  = ACONFIGURATION_KEYBOARD_NOKEYS,
        KEYBOARD_QWERTY  = ACONFIGURATION_KEYBOARD_QWERTY,
        KEYBOARD_12KEY  = ACONFIGURATION_KEYBOARD_12KEY,
    };
    
    enum {
        NAVIGATION_ANY  = ACONFIGURATION_NAVIGATION_ANY,
        NAVIGATION_NONAV  = ACONFIGURATION_NAVIGATION_NONAV,
        NAVIGATION_DPAD  = ACONFIGURATION_NAVIGATION_DPAD,
        NAVIGATION_TRACKBALL  = ACONFIGURATION_NAVIGATION_TRACKBALL,
        NAVIGATION_WHEEL  = ACONFIGURATION_NAVIGATION_WHEEL,
    };
    
    enum {
        MASK_KEYSHIDDEN = 0x0003,
        KEYSHIDDEN_ANY = ACONFIGURATION_KEYSHIDDEN_ANY,
        KEYSHIDDEN_NO = ACONFIGURATION_KEYSHIDDEN_NO,
        KEYSHIDDEN_YES = ACONFIGURATION_KEYSHIDDEN_YES,
        KEYSHIDDEN_SOFT = ACONFIGURATION_KEYSHIDDEN_SOFT,
    };
    
    enum {
        MASK_NAVHIDDEN = 0x000c,
        SHIFT_NAVHIDDEN = 2,
        NAVHIDDEN_ANY = ACONFIGURATION_NAVHIDDEN_ANY << SHIFT_NAVHIDDEN,
        NAVHIDDEN_NO = ACONFIGURATION_NAVHIDDEN_NO << SHIFT_NAVHIDDEN,
        NAVHIDDEN_YES = ACONFIGURATION_NAVHIDDEN_YES << SHIFT_NAVHIDDEN,
    };
    
    union {
        struct {
            uint8_t keyboard;
            uint8_t navigation;
            uint8_t inputFlags;
            uint8_t inputPad0;
        };
        uint32_t input;
    };
    
    enum {
        SCREENWIDTH_ANY = 0
    };
    
    enum {
        SCREENHEIGHT_ANY = 0
    };
    
    union {
        struct {
            uint16_t screenWidth;
            uint16_t screenHeight;
        };
        uint32_t screenSize;
    };
    
    enum {
        SDKVERSION_ANY = 0
    };
    
    enum {
        MINORVERSION_ANY = 0
    };
    
    union {
        struct {
            uint16_t sdkVersion;
            // For now minorVersion must always be 0!!!  Its meaning
            // is currently undefined.
            uint16_t minorVersion;
        };
        uint32_t version;
    };
    
    enum {
        // screenLayout bits for screen size class.
        MASK_SCREENSIZE = 0x0f,
        SCREENSIZE_ANY = ACONFIGURATION_SCREENSIZE_ANY,
        SCREENSIZE_SMALL = ACONFIGURATION_SCREENSIZE_SMALL,
        SCREENSIZE_NORMAL = ACONFIGURATION_SCREENSIZE_NORMAL,
        SCREENSIZE_LARGE = ACONFIGURATION_SCREENSIZE_LARGE,
        SCREENSIZE_XLARGE = ACONFIGURATION_SCREENSIZE_XLARGE,
        
        // screenLayout bits for wide/long screen variation.
        MASK_SCREENLONG = 0x30,
        SHIFT_SCREENLONG = 4,
        SCREENLONG_ANY = ACONFIGURATION_SCREENLONG_ANY << SHIFT_SCREENLONG,
        SCREENLONG_NO = ACONFIGURATION_SCREENLONG_NO << SHIFT_SCREENLONG,
        SCREENLONG_YES = ACONFIGURATION_SCREENLONG_YES << SHIFT_SCREENLONG,
    };
    
    enum {
        // uiMode bits for the mode type.
        MASK_UI_MODE_TYPE = 0x0f,
        UI_MODE_TYPE_ANY = ACONFIGURATION_UI_MODE_TYPE_ANY,
        UI_MODE_TYPE_NORMAL = ACONFIGURATION_UI_MODE_TYPE_NORMAL,
        UI_MODE_TYPE_DESK = ACONFIGURATION_UI_MODE_TYPE_DESK,
        UI_MODE_TYPE_CAR = ACONFIGURATION_UI_MODE_TYPE_CAR,
        UI_MODE_TYPE_TELEVISION = ACONFIGURATION_UI_MODE_TYPE_TELEVISION,

        // uiMode bits for the night switch.
        MASK_UI_MODE_NIGHT = 0x30,
        SHIFT_UI_MODE_NIGHT = 4,
        UI_MODE_NIGHT_ANY = ACONFIGURATION_UI_MODE_NIGHT_ANY << SHIFT_UI_MODE_NIGHT,
        UI_MODE_NIGHT_NO = ACONFIGURATION_UI_MODE_NIGHT_NO << SHIFT_UI_MODE_NIGHT,
        UI_MODE_NIGHT_YES = ACONFIGURATION_UI_MODE_NIGHT_YES << SHIFT_UI_MODE_NIGHT,
    };

    union {
        struct {
            uint8_t screenLayout;
            uint8_t uiMode;
            uint16_t smallestScreenWidthDp;
        };
        uint32_t screenConfig;
    };
    
    union {
        struct {
            uint16_t screenWidthDp;
            uint16_t screenHeightDp;
        };
        uint32_t screenSizeDp;
    };

    inline void copyFromDeviceNoSwap(const ResTable_config& o) {
        const size_t size = dtohl(o.size);
        if (size >= sizeof(ResTable_config)) {
            *this = o;
        } else {
            memcpy(this, &o, size);
            memset(((uint8_t*)this)+size, 0, sizeof(ResTable_config)-size);
        }
    }
    
    inline void copyFromDtoH(const ResTable_config& o) {
        copyFromDeviceNoSwap(o);
        size = sizeof(ResTable_config);
        mcc = dtohs(mcc);
        mnc = dtohs(mnc);
        density = dtohs(density);
        screenWidth = dtohs(screenWidth);
        screenHeight = dtohs(screenHeight);
        sdkVersion = dtohs(sdkVersion);
        minorVersion = dtohs(minorVersion);
        smallestScreenWidthDp = dtohs(smallestScreenWidthDp);
        screenWidthDp = dtohs(screenWidthDp);
        screenHeightDp = dtohs(screenHeightDp);
    }
    
    inline void swapHtoD() {
        size = htodl(size);
        mcc = htods(mcc);
        mnc = htods(mnc);
        density = htods(density);
        screenWidth = htods(screenWidth);
        screenHeight = htods(screenHeight);
        sdkVersion = htods(sdkVersion);
        minorVersion = htods(minorVersion);
        smallestScreenWidthDp = htods(smallestScreenWidthDp);
        screenWidthDp = htods(screenWidthDp);
        screenHeightDp = htods(screenHeightDp);
    }
    
    inline int compare(const ResTable_config& o) const {
        int32_t diff = (int32_t)(imsi - o.imsi);
        if (diff != 0) return diff;
        diff = (int32_t)(locale - o.locale);
        if (diff != 0) return diff;
        diff = (int32_t)(screenType - o.screenType);
        if (diff != 0) return diff;
        diff = (int32_t)(input - o.input);
        if (diff != 0) return diff;
        diff = (int32_t)(screenSize - o.screenSize);
        if (diff != 0) return diff;
        diff = (int32_t)(version - o.version);
        if (diff != 0) return diff;
        diff = (int32_t)(screenLayout - o.screenLayout);
        if (diff != 0) return diff;
        diff = (int32_t)(uiMode - o.uiMode);
        if (diff != 0) return diff;
        diff = (int32_t)(smallestScreenWidthDp - o.smallestScreenWidthDp);
        if (diff != 0) return diff;
        diff = (int32_t)(screenSizeDp - o.screenSizeDp);
        return (int)diff;
    }
    
    // Flags indicating a set of config values.  These flag constants must
    // match the corresponding ones in android.content.pm.ActivityInfo and
    // attrs_manifest.xml.
    enum {
        CONFIG_MCC = ACONFIGURATION_MCC,
        CONFIG_MNC = ACONFIGURATION_MCC,
        CONFIG_LOCALE = ACONFIGURATION_LOCALE,
        CONFIG_TOUCHSCREEN = ACONFIGURATION_TOUCHSCREEN,
        CONFIG_KEYBOARD = ACONFIGURATION_KEYBOARD,
        CONFIG_KEYBOARD_HIDDEN = ACONFIGURATION_KEYBOARD_HIDDEN,
        CONFIG_NAVIGATION = ACONFIGURATION_NAVIGATION,
        CONFIG_ORIENTATION = ACONFIGURATION_ORIENTATION,
        CONFIG_DENSITY = ACONFIGURATION_DENSITY,
        CONFIG_SCREEN_SIZE = ACONFIGURATION_SCREEN_SIZE,
        CONFIG_SMALLEST_SCREEN_SIZE = ACONFIGURATION_SMALLEST_SCREEN_SIZE,
        CONFIG_VERSION = ACONFIGURATION_VERSION,
        CONFIG_SCREEN_LAYOUT = ACONFIGURATION_SCREEN_LAYOUT,
        CONFIG_UI_MODE = ACONFIGURATION_UI_MODE
    };
    
    // Compare two configuration, returning CONFIG_* flags set for each value
    // that is different.
    inline int diff(const ResTable_config& o) const {
        int diffs = 0;
        if (mcc != o.mcc) diffs |= CONFIG_MCC;
        if (mnc != o.mnc) diffs |= CONFIG_MNC;
        if (locale != o.locale) diffs |= CONFIG_LOCALE;
        if (orientation != o.orientation) diffs |= CONFIG_ORIENTATION;
        if (density != o.density) diffs |= CONFIG_DENSITY;
        if (touchscreen != o.touchscreen) diffs |= CONFIG_TOUCHSCREEN;
        if (((inputFlags^o.inputFlags)&(MASK_KEYSHIDDEN|MASK_NAVHIDDEN)) != 0)
                diffs |= CONFIG_KEYBOARD_HIDDEN;
        if (keyboard != o.keyboard) diffs |= CONFIG_KEYBOARD;
        if (navigation != o.navigation) diffs |= CONFIG_NAVIGATION;
        if (screenSize != o.screenSize) diffs |= CONFIG_SCREEN_SIZE;
        if (version != o.version) diffs |= CONFIG_VERSION;
        if (screenLayout != o.screenLayout) diffs |= CONFIG_SCREEN_LAYOUT;
        if (uiMode != o.uiMode) diffs |= CONFIG_UI_MODE;
        if (smallestScreenWidthDp != o.smallestScreenWidthDp) diffs |= CONFIG_SMALLEST_SCREEN_SIZE;
        if (screenSizeDp != o.screenSizeDp) diffs |= CONFIG_SCREEN_SIZE;
        return diffs;
    }
    
    // Return true if 'this' is more specific than 'o'.
    inline bool
    isMoreSpecificThan(const ResTable_config& o) const {
        // The order of the following tests defines the importance of one
        // configuration parameter over another.  Those tests first are more
        // important, trumping any values in those following them.
        if (imsi || o.imsi) {
            if (mcc != o.mcc) {
                if (!mcc) return false;
                if (!o.mcc) return true;
            }

            if (mnc != o.mnc) {
                if (!mnc) return false;
                if (!o.mnc) return true;
            }
        }

        if (locale || o.locale) {
            if (language[0] != o.language[0]) {
                if (!language[0]) return false;
                if (!o.language[0]) return true;
            }

            if (country[0] != o.country[0]) {
                if (!country[0]) return false;
                if (!o.country[0]) return true;
            }
        }

        if (smallestScreenWidthDp || o.smallestScreenWidthDp) {
            if (smallestScreenWidthDp != o.smallestScreenWidthDp) {
                if (!smallestScreenWidthDp) return false;
                if (!o.smallestScreenWidthDp) return true;
            }
        }

        if (screenSizeDp || o.screenSizeDp) {
            if (screenWidthDp != o.screenWidthDp) {
                if (!screenWidthDp) return false;
                if (!o.screenWidthDp) return true;
            }

            if (screenHeightDp != o.screenHeightDp) {
                if (!screenHeightDp) return false;
                if (!o.screenHeightDp) return true;
            }
        }

        if (screenLayout || o.screenLayout) {
            if (((screenLayout^o.screenLayout) & MASK_SCREENSIZE) != 0) {
                if (!(screenLayout & MASK_SCREENSIZE)) return false;
                if (!(o.screenLayout & MASK_SCREENSIZE)) return true;
            }
            if (((screenLayout^o.screenLayout) & MASK_SCREENLONG) != 0) {
                if (!(screenLayout & MASK_SCREENLONG)) return false;
                if (!(o.screenLayout & MASK_SCREENLONG)) return true;
            }
        }

        if (orientation != o.orientation) {
            if (!orientation) return false;
            if (!o.orientation) return true;
        }

        if (uiMode || o.uiMode) {
            if (((uiMode^o.uiMode) & MASK_UI_MODE_TYPE) != 0) {
                if (!(uiMode & MASK_UI_MODE_TYPE)) return false;
                if (!(o.uiMode & MASK_UI_MODE_TYPE)) return true;
            }
            if (((uiMode^o.uiMode) & MASK_UI_MODE_NIGHT) != 0) {
                if (!(uiMode & MASK_UI_MODE_NIGHT)) return false;
                if (!(o.uiMode & MASK_UI_MODE_NIGHT)) return true;
            }
        }

        // density is never 'more specific'
        // as the default just equals 160

        if (touchscreen != o.touchscreen) {
            if (!touchscreen) return false;
            if (!o.touchscreen) return true;
        }

        if (input || o.input) {
            if (((inputFlags^o.inputFlags) & MASK_KEYSHIDDEN) != 0) {
                if (!(inputFlags & MASK_KEYSHIDDEN)) return false;
                if (!(o.inputFlags & MASK_KEYSHIDDEN)) return true;
            }

            if (((inputFlags^o.inputFlags) & MASK_NAVHIDDEN) != 0) {
                if (!(inputFlags & MASK_NAVHIDDEN)) return false;
                if (!(o.inputFlags & MASK_NAVHIDDEN)) return true;
            }

            if (keyboard != o.keyboard) {
                if (!keyboard) return false;
                if (!o.keyboard) return true;
            }

            if (navigation != o.navigation) {
                if (!navigation) return false;
                if (!o.navigation) return true;
            }
        }

        if (screenSize || o.screenSize) {
            if (screenWidth != o.screenWidth) {
                if (!screenWidth) return false;
                if (!o.screenWidth) return true;
            }

            if (screenHeight != o.screenHeight) {
                if (!screenHeight) return false;
                if (!o.screenHeight) return true;
            }
        }

        if (version || o.version) {
            if (sdkVersion != o.sdkVersion) {
                if (!sdkVersion) return false;
                if (!o.sdkVersion) return true;
            }

            if (minorVersion != o.minorVersion) {
                if (!minorVersion) return false;
                if (!o.minorVersion) return true;
            }
        }
        return false;
    }

    // Return true if 'this' is a better match than 'o' for the 'requested'
    // configuration.  This assumes that match() has already been used to
    // remove any configurations that don't match the requested configuration
    // at all; if they are not first filtered, non-matching results can be
    // considered better than matching ones.
    // The general rule per attribute: if the request cares about an attribute
    // (it normally does), if the two (this and o) are equal it's a tie.  If
    // they are not equal then one must be generic because only generic and
    // '==requested' will pass the match() call.  So if this is not generic,
    // it wins.  If this IS generic, o wins (return false).
    inline bool
    isBetterThan(const ResTable_config& o,
            const ResTable_config* requested) const {
        if (requested) {
            if (imsi || o.imsi) {
                if ((mcc != o.mcc) && requested->mcc) {
                    return (mcc);
                }

                if ((mnc != o.mnc) && requested->mnc) {
                    return (mnc);
                }
            }

            if (locale || o.locale) {
                if ((language[0] != o.language[0]) && requested->language[0]) {
                    return (language[0]);
                }

                if ((country[0] != o.country[0]) && requested->country[0]) {
                    return (country[0]);
                }
            }

            if (smallestScreenWidthDp || o.smallestScreenWidthDp) {
                // The configuration closest to the actual size is best.
                // We assume that larger configs have already been filtered
                // out at this point.  That means we just want the largest one.
                return smallestScreenWidthDp >= o.smallestScreenWidthDp;
            }

            if (screenSizeDp || o.screenSizeDp) {
                // "Better" is based on the sum of the difference between both
                // width and height from the requested dimensions.  We are
                // assuming the invalid configs (with smaller dimens) have
                // already been filtered.  Note that if a particular dimension
                // is unspecified, we will end up with a large value (the
                // difference between 0 and the requested dimension), which is
                // good since we will prefer a config that has specified a
                // dimension value.
                int myDelta = 0, otherDelta = 0;
                if (requested->screenWidthDp) {
                    myDelta += requested->screenWidthDp - screenWidthDp;
                    otherDelta += requested->screenWidthDp - o.screenWidthDp;
                }
                if (requested->screenHeightDp) {
                    myDelta += requested->screenHeightDp - screenHeightDp;
                    otherDelta += requested->screenHeightDp - o.screenHeightDp;
                }
                //LOGI("Comparing this %dx%d to other %dx%d in %dx%d: myDelta=%d otherDelta=%d",
                //    screenWidthDp, screenHeightDp, o.screenWidthDp, o.screenHeightDp,
                //    requested->screenWidthDp, requested->screenHeightDp, myDelta, otherDelta);
                return (myDelta <= otherDelta);
            }

            if (screenLayout || o.screenLayout) {
                if (((screenLayout^o.screenLayout) & MASK_SCREENSIZE) != 0
                        && (requested->screenLayout & MASK_SCREENSIZE)) {
                    // A little backwards compatibility here: undefined is
                    // considered equivalent to normal.  But only if the
                    // requested size is at least normal; otherwise, small
                    // is better than the default.
                    int mySL = (screenLayout & MASK_SCREENSIZE);
                    int oSL = (o.screenLayout & MASK_SCREENSIZE);
                    int fixedMySL = mySL;
                    int fixedOSL = oSL;
                    if ((requested->screenLayout & MASK_SCREENSIZE) >= SCREENSIZE_NORMAL) {
                        if (fixedMySL == 0) fixedMySL = SCREENSIZE_NORMAL;
                        if (fixedOSL == 0) fixedOSL = SCREENSIZE_NORMAL;
                    }
                    // For screen size, the best match is the one that is
                    // closest to the requested screen size, but not over
                    // (the not over part is dealt with in match() below).
                    if (fixedMySL == fixedOSL) {
                        // If the two are the same, but 'this' is actually
                        // undefined, then the other is really a better match.
                        if (mySL == 0) return false;
                        return true;
                    }
                    return fixedMySL >= fixedOSL;
                }
                if (((screenLayout^o.screenLayout) & MASK_SCREENLONG) != 0
                        && (requested->screenLayout & MASK_SCREENLONG)) {
                    return (screenLayout & MASK_SCREENLONG);
                }
            }

            if ((orientation != o.orientation) && requested->orientation) {
                return (orientation);
            }

            if (uiMode || o.uiMode) {
                if (((uiMode^o.uiMode) & MASK_UI_MODE_TYPE) != 0
                        && (requested->uiMode & MASK_UI_MODE_TYPE)) {
                    return (uiMode & MASK_UI_MODE_TYPE);
                }
                if (((uiMode^o.uiMode) & MASK_UI_MODE_NIGHT) != 0
                        && (requested->uiMode & MASK_UI_MODE_NIGHT)) {
                    return (uiMode & MASK_UI_MODE_NIGHT);
                }
            }

            if (screenType || o.screenType) {
                if (density != o.density) {
                    // density is tough.  Any density is potentially useful
                    // because the system will scale it.  Scaling down
                    // is generally better than scaling up.
                    // Default density counts as 160dpi (the system default)
                    // TODO - remove 160 constants
                    int h = (density?density:160);
                    int l = (o.density?o.density:160);
                    bool bImBigger = true;
                    if (l > h) {
                        int t = h;
                        h = l;
                        l = t;
                        bImBigger = false;
                    }
 
                    int reqValue = (requested->density?requested->density:160);
                    if (reqValue >= h) {
                        // requested value higher than both l and h, give h
                        return bImBigger;
                    }
                    if (l >= reqValue) {
                        // requested value lower than both l and h, give l
                        return !bImBigger;
                    }
                    // saying that scaling down is 2x better than up
                    if (((2 * l) - reqValue) * h > reqValue * reqValue) {
                        return !bImBigger;
                    } else { 
                        return bImBigger;
                    }
                }

                if ((touchscreen != o.touchscreen) && requested->touchscreen) {
                    return (touchscreen);
                }
            }

            if (input || o.input) {
                const int keysHidden = inputFlags & MASK_KEYSHIDDEN;
                const int oKeysHidden = o.inputFlags & MASK_KEYSHIDDEN;
                if (keysHidden != oKeysHidden) {
                    const int reqKeysHidden =
                            requested->inputFlags & MASK_KEYSHIDDEN;
                    if (reqKeysHidden) {

                        if (!keysHidden) return false;
                        if (!oKeysHidden) return true;
                        // For compatibility, we count KEYSHIDDEN_NO as being
                        // the same as KEYSHIDDEN_SOFT.  Here we disambiguate
                        // these by making an exact match more specific.
                        if (reqKeysHidden == keysHidden) return true;
                        if (reqKeysHidden == oKeysHidden) return false;
                    }
                }

                const int navHidden = inputFlags & MASK_NAVHIDDEN;
                const int oNavHidden = o.inputFlags & MASK_NAVHIDDEN;
                if (navHidden != oNavHidden) {
                    const int reqNavHidden =
                            requested->inputFlags & MASK_NAVHIDDEN;
                    if (reqNavHidden) {

                        if (!navHidden) return false;
                        if (!oNavHidden) return true;
                    }
                }

                if ((keyboard != o.keyboard) && requested->keyboard) {
                    return (keyboard);
                }

                if ((navigation != o.navigation) && requested->navigation) {
                    return (navigation);
                }
            }

            if (screenSize || o.screenSize) {
                // "Better" is based on the sum of the difference between both
                // width and height from the requested dimensions.  We are
                // assuming the invalid configs (with smaller sizes) have
                // already been filtered.  Note that if a particular dimension
                // is unspecified, we will end up with a large value (the
                // difference between 0 and the requested dimension), which is
                // good since we will prefer a config that has specified a
                // size value.
                int myDelta = 0, otherDelta = 0;
                if (requested->screenWidth) {
                    myDelta += requested->screenWidth - screenWidth;
                    otherDelta += requested->screenWidth - o.screenWidth;
                }
                if (requested->screenHeight) {
                    myDelta += requested->screenHeight - screenHeight;
                    otherDelta += requested->screenHeight - o.screenHeight;
                }
                return (myDelta <= otherDelta);
            }

            if (version || o.version) {
                if ((sdkVersion != o.sdkVersion) && requested->sdkVersion) {
                    return (sdkVersion > o.sdkVersion);
                }

                if ((minorVersion != o.minorVersion) &&
                        requested->minorVersion) {
                    return (minorVersion);
                }
            }

            return false;
        }
        return isMoreSpecificThan(o);
    }

    // Return true if 'this' can be considered a match for the parameters in 
    // 'settings'.
    // Note this is asymetric.  A default piece of data will match every request
    // but a request for the default should not match odd specifics
    // (ie, request with no mcc should not match a particular mcc's data)
    // settings is the requested settings
    inline bool match(const ResTable_config& settings) const {
        if (imsi != 0) {
            if (mcc != 0 && mcc != settings.mcc) {
                return false;
            }
            if (mnc != 0 && mnc != settings.mnc) {
                return false;
            }
        }
        if (locale != 0) {
            if (language[0] != 0
                && (language[0] != settings.language[0]
                    || language[1] != settings.language[1])) {
                return false;
            }
            if (country[0] != 0
                && (country[0] != settings.country[0]
                    || country[1] != settings.country[1])) {
                return false;
            }
        }
        if (screenConfig != 0) {
            const int screenSize = screenLayout&MASK_SCREENSIZE;
            const int setScreenSize = settings.screenLayout&MASK_SCREENSIZE;
            // Any screen sizes for larger screens than the setting do not
            // match.
            if (screenSize != 0 && screenSize > setScreenSize) {
                return false;
            }
            
            const int screenLong = screenLayout&MASK_SCREENLONG;
            const int setScreenLong = settings.screenLayout&MASK_SCREENLONG;
            if (screenLong != 0 && screenLong != setScreenLong) {
                return false;
            }

            const int uiModeType = uiMode&MASK_UI_MODE_TYPE;
            const int setUiModeType = settings.uiMode&MASK_UI_MODE_TYPE;
            if (uiModeType != 0 && uiModeType != setUiModeType) {
                return false;
            }

            const int uiModeNight = uiMode&MASK_UI_MODE_NIGHT;
            const int setUiModeNight = settings.uiMode&MASK_UI_MODE_NIGHT;
            if (uiModeNight != 0 && uiModeNight != setUiModeNight) {
                return false;
            }

            if (smallestScreenWidthDp != 0
                    && smallestScreenWidthDp > settings.smallestScreenWidthDp) {
                return false;
            }
        }
        if (screenSizeDp != 0) {
            if (screenWidthDp != 0 && screenWidthDp > settings.screenWidthDp) {
                //LOGI("Filtering out width %d in requested %d", screenWidthDp, settings.screenWidthDp);
                return false;
            }
            if (screenHeightDp != 0 && screenHeightDp > settings.screenHeightDp) {
                //LOGI("Filtering out height %d in requested %d", screenHeightDp, settings.screenHeightDp);
                return false;
            }
        }
        if (screenType != 0) {
            if (orientation != 0 && orientation != settings.orientation) {
                return false;
            }
            // density always matches - we can scale it.  See isBetterThan
            if (touchscreen != 0 && touchscreen != settings.touchscreen) {
                return false;
            }
        }
        if (input != 0) {
            const int keysHidden = inputFlags&MASK_KEYSHIDDEN;
            const int setKeysHidden = settings.inputFlags&MASK_KEYSHIDDEN;
            if (keysHidden != 0 && keysHidden != setKeysHidden) {
                // For compatibility, we count a request for KEYSHIDDEN_NO as also
                // matching the more recent KEYSHIDDEN_SOFT.  Basically
                // KEYSHIDDEN_NO means there is some kind of keyboard available.
                //LOGI("Matching keysHidden: have=%d, config=%d\n", keysHidden, setKeysHidden);
                if (keysHidden != KEYSHIDDEN_NO || setKeysHidden != KEYSHIDDEN_SOFT) {
                    //LOGI("No match!");
                    return false;
                }
            }
            const int navHidden = inputFlags&MASK_NAVHIDDEN;
            const int setNavHidden = settings.inputFlags&MASK_NAVHIDDEN;
            if (navHidden != 0 && navHidden != setNavHidden) {
                return false;
            }
            if (keyboard != 0 && keyboard != settings.keyboard) {
                return false;
            }
            if (navigation != 0 && navigation != settings.navigation) {
                return false;
            }
        }
        if (screenSize != 0) {
            if (screenWidth != 0 && screenWidth > settings.screenWidth) {
                return false;
            }
            if (screenHeight != 0 && screenHeight > settings.screenHeight) {
                return false;
            }
        }
        if (version != 0) {
            if (sdkVersion != 0 && sdkVersion > settings.sdkVersion) {
                return false;
            }
            if (minorVersion != 0 && minorVersion != settings.minorVersion) {
                return false;
            }
        }
        return true;
    }

    void getLocale(char str[6]) const {
        memset(str, 0, 6);
        if (language[0]) {
            str[0] = language[0];
            str[1] = language[1];
            if (country[0]) {
                str[2] = '_';
                str[3] = country[0];
                str[4] = country[1];
            }
        }
    }

    String8 toString() const {
        char buf[200];
        sprintf(buf, "imsi=%d/%d lang=%c%c reg=%c%c orient=%d touch=%d dens=%d "
                "kbd=%d nav=%d input=%d ssz=%dx%d sw%ddp w%ddp h%ddp sz=%d long=%d "
                "ui=%d night=%d vers=%d.%d",
                mcc, mnc,
                language[0] ? language[0] : '-', language[1] ? language[1] : '-',
                country[0] ? country[0] : '-', country[1] ? country[1] : '-',
                orientation, touchscreen, density, keyboard, navigation, inputFlags,
                screenWidth, screenHeight, smallestScreenWidthDp, screenWidthDp, screenHeightDp,
                screenLayout&MASK_SCREENSIZE, screenLayout&MASK_SCREENLONG,
                uiMode&MASK_UI_MODE_TYPE, uiMode&MASK_UI_MODE_NIGHT,
                sdkVersion, minorVersion);
        return String8(buf);
    }
};

/**
 * A specification of the resources defined by a particular type.
 *
 * There should be one of these chunks for each resource type.
 *
 * This structure is followed by an array of integers providing the set of
 * configuation change flags (ResTable_config::CONFIG_*) that have multiple
 * resources for that configuration.  In addition, the high bit is set if that
 * resource has been made public.
 */
struct ResTable_typeSpec
{
    struct ResChunk_header header;

    // The type identifier this chunk is holding.  Type IDs start
    // at 1 (corresponding to the value of the type bits in a
    // resource identifier).  0 is invalid.
    uint8_t id;
    
    // Must be 0.
    uint8_t res0;
    // Must be 0.
    uint16_t res1;
    
    // Number of uint32_t entry configuration masks that follow.
    uint32_t entryCount;

    enum {
        // Additional flag indicating an entry is public.
        SPEC_PUBLIC = 0x40000000
    };
};

/**
 * A collection of resource entries for a particular resource data
 * type. Followed by an array of uint32_t defining the resource
 * values, corresponding to the array of type strings in the
 * ResTable_package::typeStrings string block. Each of these hold an
 * index from entriesStart; a value of NO_ENTRY means that entry is
 * not defined.
 *
 * There may be multiple of these chunks for a particular resource type,
 * supply different configuration variations for the resource values of
 * that type.
 *
 * It would be nice to have an additional ordered index of entries, so
 * we can do a binary search if trying to find a resource by string name.
 */
struct ResTable_type
{
    struct ResChunk_header header;

    enum {
        NO_ENTRY = 0xFFFFFFFF
    };
    
    // The type identifier this chunk is holding.  Type IDs start
    // at 1 (corresponding to the value of the type bits in a
    // resource identifier).  0 is invalid.
    uint8_t id;
    
    // Must be 0.
    uint8_t res0;
    // Must be 0.
    uint16_t res1;
    
    // Number of uint32_t entry indices that follow.
    uint32_t entryCount;

    // Offset from header where ResTable_entry data starts.
    uint32_t entriesStart;
    
    // Configuration this collection of entries is designed for.
    ResTable_config config;
};

/**
 * This is the beginning of information about an entry in the resource
 * table.  It holds the reference to the name of this entry, and is
 * immediately followed by one of:
 *   * A Res_value structure, if FLAG_COMPLEX is -not- set.
 *   * An array of ResTable_map structures, if FLAG_COMPLEX is set.
 *     These supply a set of name/value mappings of data.
 */
struct ResTable_entry
{
    // Number of bytes in this structure.
    uint16_t size;

    enum {
        // If set, this is a complex entry, holding a set of name/value
        // mappings.  It is followed by an array of ResTable_map structures.
        FLAG_COMPLEX = 0x0001,
        // If set, this resource has been declared public, so libraries
        // are allowed to reference it.
        FLAG_PUBLIC = 0x0002
    };
    uint16_t flags;
    
    // Reference into ResTable_package::keyStrings identifying this entry.
    struct ResStringPool_ref key;
};

/**
 * Extended form of a ResTable_entry for map entries, defining a parent map
 * resource from which to inherit values.
 */
struct ResTable_map_entry : public ResTable_entry
{
    // Resource identifier of the parent mapping, or 0 if there is none.
    ResTable_ref parent;
    // Number of name/value pairs that follow for FLAG_COMPLEX.
    uint32_t count;
};

/**
 * A single name/value mapping that is part of a complex resource
 * entry.
 */
struct ResTable_map
{
    // The resource identifier defining this mapping's name.  For attribute
    // resources, 'name' can be one of the following special resource types
    // to supply meta-data about the attribute; for all other resource types
    // it must be an attribute resource.
    ResTable_ref name;

    // Special values for 'name' when defining attribute resources.
    enum {
        // This entry holds the attribute's type code.
        ATTR_TYPE = Res_MAKEINTERNAL(0),

        // For integral attributes, this is the minimum value it can hold.
        ATTR_MIN = Res_MAKEINTERNAL(1),

        // For integral attributes, this is the maximum value it can hold.
        ATTR_MAX = Res_MAKEINTERNAL(2),

        // Localization of this resource is can be encouraged or required with
        // an aapt flag if this is set
        ATTR_L10N = Res_MAKEINTERNAL(3),

        // for plural support, see android.content.res.PluralRules#attrForQuantity(int)
        ATTR_OTHER = Res_MAKEINTERNAL(4),
        ATTR_ZERO = Res_MAKEINTERNAL(5),
        ATTR_ONE = Res_MAKEINTERNAL(6),
        ATTR_TWO = Res_MAKEINTERNAL(7),
        ATTR_FEW = Res_MAKEINTERNAL(8),
        ATTR_MANY = Res_MAKEINTERNAL(9)
        
    };

    // Bit mask of allowed types, for use with ATTR_TYPE.
    enum {
        // No type has been defined for this attribute, use generic
        // type handling.  The low 16 bits are for types that can be
        // handled generically; the upper 16 require additional information
        // in the bag so can not be handled generically for TYPE_ANY.
        TYPE_ANY = 0x0000FFFF,

        // Attribute holds a references to another resource.
        TYPE_REFERENCE = 1<<0,

        // Attribute holds a generic string.
        TYPE_STRING = 1<<1,

        // Attribute holds an integer value.  ATTR_MIN and ATTR_MIN can
        // optionally specify a constrained range of possible integer values.
        TYPE_INTEGER = 1<<2,

        // Attribute holds a boolean integer.
        TYPE_BOOLEAN = 1<<3,

        // Attribute holds a color value.
        TYPE_COLOR = 1<<4,

        // Attribute holds a floating point value.
        TYPE_FLOAT = 1<<5,

        // Attribute holds a dimension value, such as "20px".
        TYPE_DIMENSION = 1<<6,

        // Attribute holds a fraction value, such as "20%".
        TYPE_FRACTION = 1<<7,

        // Attribute holds an enumeration.  The enumeration values are
        // supplied as additional entries in the map.
        TYPE_ENUM = 1<<16,

        // Attribute holds a bitmaks of flags.  The flag bit values are
        // supplied as additional entries in the map.
        TYPE_FLAGS = 1<<17
    };

    // Enum of localization modes, for use with ATTR_L10N.
    enum {
        L10N_NOT_REQUIRED = 0,
        L10N_SUGGESTED    = 1
    };
    
    // This mapping's value.
    Res_value value;
};

/**
 * Convenience class for accessing data in a ResTable resource.
 */
class ResTable
{
public:
    ResTable();
    ResTable(const void* data, size_t size, void* cookie,
             bool copyData=false);
    ~ResTable();

    status_t add(const void* data, size_t size, void* cookie,
                 bool copyData=false, const void* idmap = NULL);
    status_t add(Asset* asset, void* cookie,
                 bool copyData=false, const void* idmap = NULL);
    status_t add(ResTable* src);

    void addRedirections(PackageRedirectionMap* resMap);
    void clearRedirections();

    status_t getError() const;

    void uninit();

    struct resource_name
    {
        const char16_t* package;
        size_t packageLen;
        const char16_t* type;
        size_t typeLen;
        const char16_t* name;
        size_t nameLen;
    };

    bool getResourceName(uint32_t resID, resource_name* outName) const;

    /**
     * Retrieve the value of a resource.  If the resource is found, returns a
     * value >= 0 indicating the table it is in (for use with
     * getTableStringBlock() and getTableCookie()) and fills in 'outValue'.  If
     * not found, returns a negative error code.
     *
     * Note that this function does not do reference traversal.  If you want
     * to follow references to other resources to get the "real" value to
     * use, you need to call resolveReference() after this function.
     *
     * @param resID The desired resoruce identifier.
     * @param outValue Filled in with the resource data that was found.
     *
     * @return ssize_t Either a >= 0 table index or a negative error code.
     */
    ssize_t getResource(uint32_t resID, Res_value* outValue, bool mayBeBag = false,
                    uint16_t density = 0,
                    uint32_t* outSpecFlags = NULL,
                    ResTable_config* outConfig = NULL) const;

    inline ssize_t getResource(const ResTable_ref& res, Res_value* outValue,
            uint32_t* outSpecFlags=NULL) const {
        return getResource(res.ident, outValue, false, 0, outSpecFlags, NULL);
    }

    ssize_t resolveReference(Res_value* inOutValue,
                             ssize_t blockIndex,
                             uint32_t* outLastRef = NULL,
                             uint32_t* inoutTypeSpecFlags = NULL,
                             ResTable_config* outConfig = NULL) const;

    uint32_t lookupRedirectionMap(uint32_t resID) const;

    enum {
        TMP_BUFFER_SIZE = 16
    };
    const char16_t* valueToString(const Res_value* value, size_t stringBlock,
                                  char16_t tmpBuffer[TMP_BUFFER_SIZE],
                                  size_t* outLen);

    struct bag_entry {
        ssize_t stringBlock;
        ResTable_map map;
    };

    /**
     * Retrieve the bag of a resource.  If the resoruce is found, returns the
     * number of bags it contains and 'outBag' points to an array of their
     * values.  If not found, a negative error code is returned.
     *
     * Note that this function -does- do reference traversal of the bag data.
     *
     * @param resID The desired resource identifier.
     * @param outBag Filled inm with a pointer to the bag mappings.
     *
     * @return ssize_t Either a >= 0 bag count of negative error code.
     */
    ssize_t lockBag(uint32_t resID, const bag_entry** outBag) const;

    void unlockBag(const bag_entry* bag) const;

    void lock() const;

    ssize_t getBagLocked(uint32_t resID, const bag_entry** outBag,
            uint32_t* outTypeSpecFlags=NULL) const;

    void unlock() const;

    class Theme {
    public:
        Theme(const ResTable& table);
        ~Theme();

        inline const ResTable& getResTable() const { return mTable; }

        status_t applyStyle(uint32_t resID, bool force=false);
        status_t setTo(const Theme& other);

        /**
         * Retrieve a value in the theme.  If the theme defines this
         * value, returns a value >= 0 indicating the table it is in
         * (for use with getTableStringBlock() and getTableCookie) and
         * fills in 'outValue'.  If not found, returns a negative error
         * code.
         *
         * Note that this function does not do reference traversal.  If you want
         * to follow references to other resources to get the "real" value to
         * use, you need to call resolveReference() after this function.
         *
         * @param resID A resource identifier naming the desired theme
         *              attribute.
         * @param outValue Filled in with the theme value that was
         *                 found.
         *
         * @return ssize_t Either a >= 0 table index or a negative error code.
         */
        ssize_t getAttribute(uint32_t resID, Res_value* outValue,
                uint32_t* outTypeSpecFlags = NULL) const;

        /**
         * This is like ResTable::resolveReference(), but also takes
         * care of resolving attribute references to the theme.
         */
        ssize_t resolveAttributeReference(Res_value* inOutValue,
                ssize_t blockIndex, uint32_t* outLastRef = NULL,
                uint32_t* inoutTypeSpecFlags = NULL,
                ResTable_config* inoutConfig = NULL) const;

        void dumpToLog() const;
        
    private:
        Theme(const Theme&);
        Theme& operator=(const Theme&);

        struct theme_entry {
            ssize_t stringBlock;
            uint32_t typeSpecFlags;
            Res_value value;
        };
        struct type_info {
            size_t numEntries;
            theme_entry* entries;
        };
        struct package_info {
            size_t numTypes;
            type_info types[];
        };

        void free_package(package_info* pi);
        package_info* copy_package(package_info* pi);

        const ResTable& mTable;
        package_info*   mPackages[Res_MAXPACKAGE];
    };

    void setParameters(const ResTable_config* params);
    void getParameters(ResTable_config* params) const;

    // Retrieve an identifier (which can be passed to getResource)
    // for a given resource name.  The 'name' can be fully qualified
    // (<package>:<type>.<basename>) or the package or type components
    // can be dropped if default values are supplied here.
    //
    // Returns 0 if no such resource was found, else a valid resource ID.
    uint32_t identifierForName(const char16_t* name, size_t nameLen,
                               const char16_t* type = 0, size_t typeLen = 0,
                               const char16_t* defPackage = 0,
                               size_t defPackageLen = 0,
                               uint32_t* outTypeSpecFlags = NULL) const;

    static bool expandResourceRef(const char16_t* refStr, size_t refLen,
                                  String16* outPackage,
                                  String16* outType,
                                  String16* outName,
                                  const String16* defType = NULL,
                                  const String16* defPackage = NULL,
                                  const char** outErrorMsg = NULL,
                                  bool* outPublicOnly = NULL);

    static bool stringToInt(const char16_t* s, size_t len, Res_value* outValue);
    static bool stringToFloat(const char16_t* s, size_t len, Res_value* outValue);

    // Used with stringToValue.
    class Accessor
    {
    public:
        inline virtual ~Accessor() { }

        virtual uint32_t getCustomResource(const String16& package,
                                           const String16& type,
                                           const String16& name) const = 0;
        virtual uint32_t getCustomResourceWithCreation(const String16& package,
                                                       const String16& type,
                                                       const String16& name,
                                                       const bool createIfNeeded = false) = 0;
        virtual uint32_t getRemappedPackage(uint32_t origPackage) const = 0;
        virtual bool getAttributeType(uint32_t attrID, uint32_t* outType) = 0;
        virtual bool getAttributeMin(uint32_t attrID, uint32_t* outMin) = 0;
        virtual bool getAttributeMax(uint32_t attrID, uint32_t* outMax) = 0;
        virtual bool getAttributeEnum(uint32_t attrID,
                                      const char16_t* name, size_t nameLen,
                                      Res_value* outValue) = 0;
        virtual bool getAttributeFlags(uint32_t attrID,
                                       const char16_t* name, size_t nameLen,
                                       Res_value* outValue) = 0;
        virtual uint32_t getAttributeL10N(uint32_t attrID) = 0;
        virtual bool getLocalizationSetting() = 0;
        virtual void reportError(void* accessorCookie, const char* fmt, ...) = 0;
    };

    // Convert a string to a resource value.  Handles standard "@res",
    // "#color", "123", and "0x1bd" types; performs escaping of strings.
    // The resulting value is placed in 'outValue'; if it is a string type,
    // 'outString' receives the string.  If 'attrID' is supplied, the value is
    // type checked against this attribute and it is used to perform enum
    // evaluation.  If 'acccessor' is supplied, it will be used to attempt to
    // resolve resources that do not exist in this ResTable.  If 'attrType' is
    // supplied, the value will be type checked for this format if 'attrID'
    // is not supplied or found.
    bool stringToValue(Res_value* outValue, String16* outString,
                       const char16_t* s, size_t len,
                       bool preserveSpaces, bool coerceType,
                       uint32_t attrID = 0,
                       const String16* defType = NULL,
                       const String16* defPackage = NULL,
                       Accessor* accessor = NULL,
                       void* accessorCookie = NULL,
                       uint32_t attrType = ResTable_map::TYPE_ANY,
                       bool enforcePrivate = true) const;

    // Perform processing of escapes and quotes in a string.
    static bool collectString(String16* outString,
                              const char16_t* s, size_t len,
                              bool preserveSpaces,
                              const char** outErrorMsg = NULL,
                              bool append = false);

    size_t getBasePackageCount() const;
    const char16_t* getBasePackageName(size_t idx) const;
    uint32_t getBasePackageId(size_t idx) const;

    size_t getTableCount() const;
    const ResStringPool* getTableStringBlock(size_t index) const;
    void* getTableCookie(size_t index) const;

    // Return the configurations (ResTable_config) that we know about
    void getConfigurations(Vector<ResTable_config>* configs) const;

    void getLocales(Vector<String8>* locales) const;

    // Generate an idmap.
    //
    // Return value: on success: NO_ERROR; caller is responsible for free-ing
    // outData (using free(3)). On failure, any status_t value other than
    // NO_ERROR; the caller should not free outData.
    status_t createIdmap(const ResTable& overlay, uint32_t originalCrc, uint32_t overlayCrc,
                         void** outData, size_t* outSize) const;

    enum {
        IDMAP_HEADER_SIZE_BYTES = 3 * sizeof(uint32_t),
    };
    // Retrieve idmap meta-data.
    //
    // This function only requires the idmap header (the first
    // IDMAP_HEADER_SIZE_BYTES) bytes of an idmap file.
    static bool getIdmapInfo(const void* idmap, size_t size,
                             uint32_t* pOriginalCrc, uint32_t* pOverlayCrc);
    void removeAssetsByCookie(const String8 &packageName, void* cookie);

#ifndef HAVE_ANDROID_OS
    void print(bool inclValues) const;
    static String8 normalizeForOutput(const char* input);
#endif

private:
    struct Header;
    struct Type;
    struct Package;
    struct PackageGroup;
    struct bag_set;

    status_t add(const void* data, size_t size, void* cookie,
                 Asset* asset, bool copyData, const Asset* idmap);

    ssize_t getResourcePackageIndex(uint32_t resID) const;
    ssize_t getEntry(
        const Package* package, int typeIndex, int entryIndex,
        const ResTable_config* config,
        const ResTable_type** outType, const ResTable_entry** outEntry,
        const Type** outTypeClass) const;
    status_t parsePackage(
        const ResTable_package* const pkg, const Header* const header, uint32_t idmap_id);

    void print_value(const Package* pkg, const Res_value& value) const;
    
    mutable Mutex               mLock;

    status_t                    mError;

    ResTable_config             mParams;

    // Array of all resource tables.
    Vector<Header*>             mHeaders;

    // Array of packages in all resource tables.
    Vector<PackageGroup*>       mPackageGroups;

    // Mapping from resource package IDs to indices into the internal
    // package array.
    uint8_t                     mPackageMap[256];

    // Resource redirection mapping provided by the applied theme (if there is
    // one).  Resources requested which are found in this map will be
    // automatically redirected to the appropriate themed value.
    Vector<PackageRedirectionMap*> mRedirectionMap;
};

}   // namespace android

#endif // _LIBS_UTILS_RESOURCE_TYPES_H
