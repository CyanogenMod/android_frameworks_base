/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.internal.telephony;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * generic Tlv parser
 * based on this @see <a href="http://mbyd.blogspot.co.at/2011/12/tlv-parser-part-2.html"/>
 **/
class TlvUnit {
	// the tag of the tlv record
	private long tag;
	
	// data in the record
	private byte[] value;

	/**
	 * create a new tlf record
	 * @param tag
	 * @param value
	 */
	public TlvUnit(long tag, byte[] value) {
		this.tag = tag;
		this.value = value;
	}

	public long getTag() {
		return tag;
	}

	public int getLength() {
		return value.length;
	}

	public byte[] getValue() {
		return value;
	}

	public String getStringValue() {
		return stringValue();
	}

	@Override
	public String toString() {
		return "0x"+Long.toHexString(tag) + "\t" + getLength() + "\t" + stringValue();
	}

	private static final String HEXES = "0123456789ABCDEF";

	private String stringValue() {
		final StringBuilder hex = new StringBuilder(2 * value.length);
		for (final byte b : value) {
			hex.append("0x").append(HEXES.charAt((b & 0xF0) >> 4)).append(
					HEXES.charAt((b & 0x0F))).append(" ");
		}
		return hex.toString();
	}
}

public class TlvParser {
	private int tagSize;
	private int lengthSize;
	private int startIndex;
	private List<TlvUnit> list;
	private Map<Long, byte[]> map;

	/**
	 * create parser
	 * @param tagSize
	 * 		length in bytes of the tag entry
	 * @param lengthSize
	 * 		length in bytes of the length entry
	 * @param startIndex
	 * 		offset in bytes to start with parsing
	 */
	public TlvParser(int tagSize, int lengthSize, int startIndex) {
		this.tagSize = tagSize;
		this.lengthSize = lengthSize;
		this.startIndex = startIndex;
	}

	/**
	 * 
	 * @param tlvData
	 * 			byte array of tlv data
	 */
	public void parse(final byte[] tlvData) {
		int index = startIndex;
		list = new ArrayList<TlvUnit>();
		while (index + tagSize + lengthSize < tlvData.length) {
			long tag = getLong(tlvData, index, tagSize);
			index += tagSize;
			long length = getLong(tlvData, index, lengthSize);
			index += lengthSize;
			// If the length overflows our buffer, break here
			if ((index + length < 0) || (index + length > tlvData.length))
				break;
			list.add(new TlvUnit(tag, Arrays.copyOfRange(tlvData, index, (int) (index + length))));
			index += length;
		}
	}

	/**
	 * @param tlvString
	 *            hex string of tlv data
	 * @throws IllegalArgumentException
	 *             if there are characters that are not spaces or valid hex
	 *             characters
	 */
	public void parseString(String tlvString)
			throws IllegalArgumentException {
		if (tlvString == null)
			throw new NullPointerException("Can't have null TLV string");
		tlvString = tlvString.replaceAll("\\s+", "");
		if (tlvString.matches(".*[^A-Fa-f0-9].*"))
			throw new IllegalArgumentException(
					"String contains values that are not hex");
		parse(hexStringToByteArray(tlvString));
	}

	/**
	 * @return a list of the parsed descriptors
	 * @throws IllegalStateException
	 *             if called before data was parsed
	 */
	public List<TlvUnit> asList() throws IllegalStateException {
		if (list == null)
			throw new IllegalStateException("No data parsed yet");
		return list;
	}

	/**
	 * @return a map<tag, value> of the parsed descriptors
	 * @throws IllegalStateException
	 *             if called before data was parsed
	 */
	public Map<Long, byte[]> asMap() throws IllegalStateException {
		if (list == null)
			throw new IllegalStateException("TLVParser.parse() was not called");
		if (map == null) {
			createMap();
		}
		return map;
	}

	public byte[] getValueOfTag(long tagKey) {
		return asMap().get(tagKey);
	}

	/**
	 * Generates a map (key - tag, value - value) of the parsed descriptors
	 */
	private void createMap() {
		map = new HashMap<Long, byte[]>(list.size());
		for (TlvUnit tag : list) {
			if (!map.containsKey(tag.getTag())) {
				map.put(tag.getTag(), tag.getValue());
			}
		}
	}

	private long getLong(final byte[] arr, int index, int size) {
		long l = 0;
		for (int i = size - 1; i >= 0; --i, ++index) {
			l |= (arr[index] & 0xff) << (i * 8);
		}
		return l;
	}

	private byte[] hexStringToByteArray(String s) {
		byte[] b = new byte[s.length() / 2];
		for (int i = 0; i < b.length; i++) {
			int index = i * 2;
			int v = Integer.parseInt(s.substring(index, index + 2), 16);
			b[i] = (byte) v;
		}
		return b;
	}

	/**
	 * standalone test
	 * @param args
	 */
	public static void main(String[] args) {
		String[] linesToParse = {
				"621E8205422100640183024F30A503DA01028A01058B036F0606800200648800",
				"621F82054221001DC883024F3AA503DA01028A01058B036F0604800216A8880110" };
		for (String line : linesToParse) {
			System.out.println("Parsed payload:\n" + line);
    		TlvParser parser = new TlvParser(1, 1, 2);
            parser.parseString(line);
			List<TlvUnit> tlvList = parser.asList();
			for (TlvUnit tlvUnit : tlvList)
				System.out.println(tlvUnit.toString());

			byte[] value = parser.getValueOfTag(0x82);
			System.out.println("count:" + (value[4] & 0xff));
			System.out.println("record size:"
					+ (((value[2] & 0xff) << 8) + (value[3] & 0xff)));

			value = parser.getValueOfTag(0x80);
			System.out.println("size:"
					+ (((value[0] & 0xff) << 8) + (value[1] & 0xff)));
		}
	}
}
