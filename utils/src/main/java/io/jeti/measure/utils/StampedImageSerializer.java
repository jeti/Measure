package io.jeti.measure.utils;

import com.google.flatbuffers.FlatBufferBuilder;
import java.nio.ByteBuffer;

public class StampedImageSerializer implements Serializer {

    private final AndroidImageSerializer androidImageSerializer = new AndroidImageSerializer();
    private final PoseSerializer         poseSerializer         = new PoseSerializer();

    private FlatBufferBuilder encode_impl(byte[] data, int width, int height, int format,
            double[] r, double[] q) {
        FlatBufferBuilder fbb = new FlatBufferBuilder();

        int dataOff = AndroidImage.createDataVector(fbb, data);
        AndroidImage.startAndroidImage(fbb);
        AndroidImage.addData(fbb, dataOff);
        AndroidImage.addWidth(fbb, width);
        AndroidImage.addHeight(fbb, height);
        AndroidImage.addFormat(fbb, format);
        int imageOff = AndroidImage.endAndroidImage(fbb);

        int rOff = Pose.createRVector(fbb, r);
        int qOff = Pose.createQVector(fbb, q);
        Pose.startPose(fbb);
        Pose.addR(fbb, rOff);
        Pose.addQ(fbb, qOff);
        int poseOff = Pose.endPose(fbb);

        StampedImage.startStampedImage(fbb);
        StampedImage.addImage(fbb, imageOff);
        StampedImage.addPose(fbb, poseOff);
        fbb.finish(StampedImage.endStampedImage(fbb));
        return fbb;
    }

    public byte[] encode(byte[] data, int width, int height, int format, double[] r, double[] q) {
        return encode_impl(data, width, height, format, r, q).sizedByteArray();
    }

    public ByteBuffer encodeBB(byte[] data, int width, int height, int format, double[] r,
            double[] q) {
        return encode_impl(data, width, height, format, r, q).dataBuffer();
    }

    public StampedImage decode(byte[] data) {
        return decodeBB(ByteBuffer.wrap(data));
    }

    public StampedImage decodeBB(ByteBuffer bb) {
        return StampedImage.getRootAsStampedImage(bb);
    }

    @Override
    public String toString(byte[] bytes) {
        return toString(decode(bytes));
    }

    public String toString(ByteBuffer bb) {
        return toString(decodeBB(bb));
    }

    public String toString(StampedImage stampedImage) {
        return androidImageSerializer.toString(stampedImage.image()) + ", "
                + poseSerializer.toString(stampedImage.pose());
    }
}
