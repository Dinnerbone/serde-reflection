// Copyright (c) Facebook, Inc. and its affiliates
// SPDX-License-Identifier: MIT OR Apache-2.0

package lcs;

import java.lang.Exception;
import java.math.BigInteger;

import serde.Unsigned;
import serde.Int128;
import serde.Bytes;
import serde.Slice;
import serde.Unit;


public class LcsSerializer implements serde.Serializer {
    private MyByteArrayOutputStream output;

    public LcsSerializer() {
        output = new MyByteArrayOutputStream();
    }

    public void serialize_str(String value) throws Exception {
        serialize_bytes(new Bytes(value.getBytes()));
    }

    public void serialize_bytes(Bytes value) throws Exception {
        byte[] content = value.content();
        serialize_len(content.length);
        output.write(content, 0, content.length);
    }

    public void serialize_bool(Boolean value) throws Exception {
        output.write((value.booleanValue() ? 1 : 0));
    }

    public void serialize_unit(Unit value) throws Exception {
    }

    public void serialize_char(Character value) throws Exception {
        throw new Exception("Not implemented: serialize_char");
    }

    public void serialize_f32(Float value) throws Exception {
        throw new Exception("Not implemented: serialize_f32");
    }

    public void serialize_f64(Double value) throws Exception {
        throw new Exception("Not implemented: serialize_f64");
    }

    public void serialize_u8(@Unsigned Byte value) throws Exception {
        output.write(value.byteValue());
    }

    public void serialize_u16(@Unsigned Short value) throws Exception {
        short val = value.shortValue();
        output.write((byte) (val >>> 0));
        output.write((byte) (val >>> 8));
    }

    public void serialize_u32(@Unsigned Integer value) throws Exception {
        int val = value.intValue();
        output.write((byte) (val >>> 0));
        output.write((byte) (val >>> 8));
        output.write((byte) (val >>> 16));
        output.write((byte) (val >>> 24));
    }

    public void serialize_u64(@Unsigned Long value) throws Exception {
        long val = value.longValue();
        output.write((byte) (val >>> 0));
        output.write((byte) (val >>> 8));
        output.write((byte) (val >>> 16));
        output.write((byte) (val >>> 24));
        output.write((byte) (val >>> 32));
        output.write((byte) (val >>> 40));
        output.write((byte) (val >>> 48));
        output.write((byte) (val >>> 56));
    }

    public void serialize_u128(@Unsigned @Int128 BigInteger value) throws Exception {
        assert value.compareTo(BigInteger.ZERO) >= 0;
        assert value.shiftRight(128).equals(BigInteger.ZERO);
        byte[] content = value.toByteArray();
        // BigInteger.toByteArray() may add a 16th most-significant zero
        // byte for signing purpose: ignore it.
        assert content.length <= 16 || content[0] == 0;
        int len = Math.min(content.length, 16);
        // Write content in little-endian order.
        for (int i = 0; i < len; i++) {
            output.write(content[content.length - 1 - i]);
        }
        // Complete with zeros if needed.
        for (int i = len; i < 16; i++) {
            output.write(0);
        }
    }

    public void serialize_i8(Byte value) throws Exception {
        serialize_u8(value);
    }

    public void serialize_i16(Short value) throws Exception {
        serialize_u16(value);
    }

    public void serialize_i32(Integer value) throws Exception {
        serialize_u32(value);
    }

    public void serialize_i64(Long value) throws Exception {
        serialize_u64(value);
    }

    public void serialize_i128(@Int128 BigInteger value) throws Exception {
        if (value.compareTo(BigInteger.ZERO) >= 0) {
            serialize_u128(value);
        } else {
            serialize_u128(value.add(BigInteger.ONE.shiftLeft(128)));
        }
    }

    private void serialize_u32_as_uleb128(int value) {
        while ((value >>> 7) != 0) {
            output.write((value & 0x7f) | 0x80);
            value = value >>> 7;
        }
        output.write(value);
    }

    public void serialize_len(long value) throws Exception {
        serialize_u32_as_uleb128((int) value);
    }

    public void serialize_variant_index(int value) throws Exception {
        serialize_u32_as_uleb128(value);
    }

    public void serialize_option_tag(boolean value) throws Exception {
        output.write((value ? (byte) 1 : (byte) 0));
    }

    public int get_buffer_offset() {
        return output.size();
    }

    public void sort_map_entries(int[] offsets) {
        if (offsets.length <= 1) {
            return;
        }
        int offset0 = offsets[0];
        byte[] content = output.getBuffer();
        Slice[] slices = new Slice[offsets.length];
        for (int i = 0; i < offsets.length - 1; i++) {
            slices[i] = new Slice(offsets[i], offsets[i + 1]);
        }
        slices[offsets.length - 1] = new Slice(offsets[offsets.length - 1], output.size());

        java.util.Arrays.sort(slices, new java.util.Comparator<Slice>() {
            @Override
            public int compare(Slice slice1, Slice slice2) {
                return Slice.compare_bytes(content, slice1, slice2);
            }
        });

        byte[] old_content = new byte[output.size() - offset0];
        System.arraycopy(content, offset0, old_content, 0, output.size() - offset0);

        int position = offset0;
        for (int i = 0; i < offsets.length; i++) {
            int start = slices[i].start;
            int end = slices[i].end;
            System.arraycopy(old_content, start - offset0, content, position, end - start);
            position += end - start;
        }
    }

    public byte[] get_bytes() {
        return output.toByteArray();
    }

    // Local extension to provide access to the underlying buffer.
    static class MyByteArrayOutputStream extends java.io.ByteArrayOutputStream {
        byte[] getBuffer() {
            return buf;
        }
    }
}
