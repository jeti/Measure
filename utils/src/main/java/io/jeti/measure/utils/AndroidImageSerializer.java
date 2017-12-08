package io.jeti.measure.utils;

import com.google.flatbuffers.FlatBufferBuilder;
import java.nio.ByteBuffer;

public class AndroidImageSerializer implements Serializer {

    private FlatBufferBuilder encode_impl(int width, int height, byte[] data, int format) {
        FlatBufferBuilder fbb = new FlatBufferBuilder(width * height);
        int dataOffset = AndroidImage.createDataVector(fbb, data);
        AndroidImage.startAndroidImage(fbb);
        AndroidImage.addData(fbb, dataOffset);
        AndroidImage.addWidth(fbb, width);
        AndroidImage.addHeight(fbb, height);
        AndroidImage.addFormat(fbb, format);
        fbb.finish(AndroidImage.endAndroidImage(fbb));
        return fbb;
    }

    public byte[] encode(int width, int height, byte[] data, int format) {
        return encode_impl(width, height, data, format).sizedByteArray();
    }

    public ByteBuffer encodeBB(int width, int height, byte[] data, int format) {
        return encode_impl(width, height, data, format).dataBuffer();
    }

    public AndroidImage decode(byte[] data) {
        ByteBuffer bb = ByteBuffer.wrap(data);
        return AndroidImage.getRootAsAndroidImage(bb);
    }

    public AndroidImage decodeBB(ByteBuffer bb) {
        return AndroidImage.getRootAsAndroidImage(bb);
    }

    public String toString(byte[] bytes) {
        return toString(decode(bytes));
    }

    public String toString(ByteBuffer bb) {
        return toString(decodeBB(bb));
    }

    public String toString(AndroidImage AndroidImage) {
        return "(width,height,format,len(bytes)) : ( " + AndroidImage.width() + ", "
                + AndroidImage.height() + ", " + AndroidImage.format() + ", "
                + AndroidImage.dataLength() + ")";
    }
}
