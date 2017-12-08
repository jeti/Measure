package io.jeti.measure.utils;

import com.google.flatbuffers.FlatBufferBuilder;

import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.text.NumberFormat;

public class PoseSerializer implements Serializer {

    private FlatBufferBuilder encode_impl(double[] r, double[] q) {
        FlatBufferBuilder fbb = new FlatBufferBuilder(96);
        int rOff = Pose.createRVector(fbb, r);
        int qOff = Pose.createQVector(fbb, q);
        Pose.startPose(fbb);
        Pose.addR(fbb, rOff);
        Pose.addQ(fbb, qOff);
        fbb.finish(Pose.endPose(fbb));
        return fbb;
    }

    public byte[] encode(double[] r, double[] q) {
        return encode_impl(r, q).sizedByteArray();
    }

    public ByteBuffer encodeBB(double[] r, double[] q) {
        return encode_impl(r, q).dataBuffer();
    }

    public Pose decode(byte[] data) {
        return Pose.getRootAsPose(ByteBuffer.wrap(data));
    }

    public Pose decodeBB(ByteBuffer bb) {
        return Pose.getRootAsPose(bb);
    }

    public double[] getR(Pose pose) {
        double[] r = new double[pose.rLength()];
        for (int i = 0; i < pose.rLength(); i++) {
            r[i] = pose.r(i);
        }
        return r;
    }

    public double[] getQ(Pose pose) {
        double[] q = new double[pose.qLength()];
        for (int i = 0; i < pose.qLength(); i++) {
            q[i] = pose.q(i);
        }
        return q;
    }

    @Override
    public String toString(byte[] bytes) {
        return toString(decode(bytes));
    }

    public String toString(ByteBuffer bb) {
        return toString(decodeBB(bb));
    }

    public String toString(Pose pose) {
        StringBuilder builder = new StringBuilder();
        builder.append("r: [ ");
        int i;
        for (i = 0; i < pose.rLength() - 1; i++) {
            builder.append(format(pose.r(i)));
            builder.append(", ");
        }
        builder.append(format(pose.r(i)));
        builder.append(" ]        \n");
        builder.append("q: [ ");
        for (i = 0; i < pose.qLength() - 1; i++) {
            builder.append(format(pose.q(i)));
            builder.append(", ");
        }
        builder.append(format(pose.q(i)));
        builder.append(" ]");
        return builder.toString();
    }

    private static final NumberFormat formatter = new DecimalFormat(" #0.000;-#0.000");

    private String format(double d) {
        return formatter.format(d);
    }
}
