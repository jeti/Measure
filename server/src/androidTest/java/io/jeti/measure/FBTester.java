package io.jeti.measure;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import io.jeti.editables.EditableDouble;
import io.jeti.layoutparams.MatchMatch;
import io.jeti.layoutparams.MatchWrap0;
import io.jeti.layoutparams.MatchZero1;
import io.jeti.layoutparams.ZeroMatch;
import io.jeti.measure.utils.Pose;
import io.jeti.measure.utils.PoseSerializer;
import java.nio.ByteBuffer;
import java.util.Random;

public class FBTester extends Activity {

    private static final String[][] labels = { { "rx", "ry", "rz" }, { "qx", "qy", "qz", "qw" } };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout vertical = new LinearLayout(this);
        vertical.setOrientation(LinearLayout.VERTICAL);
        vertical.setLayoutParams(new MatchMatch());

        LinearLayout horizontal = new LinearLayout(this);
        horizontal.setOrientation(LinearLayout.HORIZONTAL);
        vertical.addView(horizontal, new MatchZero1());

        LinearLayout srcColumn = new LinearLayout(this);
        srcColumn.setOrientation(LinearLayout.VERTICAL);
        horizontal.addView(srcColumn, new ZeroMatch(2));

        LinearLayout dstColumn = new LinearLayout(this);
        dstColumn.setOrientation(LinearLayout.VERTICAL);
        horizontal.addView(dstColumn, new ZeroMatch(1));

        LinearLayout dstBBColumn = new LinearLayout(this);
        dstBBColumn.setOrientation(LinearLayout.VERTICAL);
        horizontal.addView(dstBBColumn, new ZeroMatch(1));

        /* Create the views which will hold all of the values */
        final EditableDouble[][] entries = new EditableDouble[labels.length][];
        final TextView[][] dsts = new TextView[labels.length][];
        final TextView[][] dstBBs = new TextView[labels.length][];
        for (int i = 0; i < labels.length; i++) {
            final String[] label = labels[i];
            final int n = label.length;
            final EditableDouble[] entry = new EditableDouble[n];
            final TextView[] dst = new TextView[n];
            final TextView[] dstBB = new TextView[n];
            for (int j = 0; j < n; j++) {
                entry[j] = new EditableDouble(this, label[j]);
                srcColumn.addView(entry[j], new MatchZero1());
                dst[j] = new TextView(this);
                dstColumn.addView(dst[j], new MatchZero1());
                dstBB[j] = new TextView(this);
                dstBBColumn.addView(dstBB[j], new MatchZero1());
            }
            entries[i] = entry;
            dsts[i] = dst;
            dstBBs[i] = dstBB;
        }

        Button button = new Button(this);
        button.setText("Encode/Decode");
        vertical.addView(button, new MatchWrap0());

        Button randomize = new Button(this);
        randomize.setText("Randomize");
        final Random random = new Random();
        randomize.setOnClickListener(v -> {
            for (EditableDouble[] entry : entries) {
                for (EditableDouble entrx : entry) {
                    entrx.set(random.nextDouble());
                }
            }
        });
        vertical.addView(randomize, new MatchWrap0());

        final PoseSerializer poseSerializer = new PoseSerializer();
        button.setOnClickListener(v -> {

            int offset = 0;
            double[] r = new double[labels[offset].length];
            for (int i = 0; i < labels[offset].length; i++) {
                r[i] = entries[offset][i].get();
            }

            offset += 1;
            double[] q = new double[labels[offset].length];
            for (int i = 0; i < labels[offset].length; i++) {
                q[i] = entries[offset][i].get();
            }

            byte[] bytes = poseSerializer.encode(r, q);
            Pose pose = poseSerializer.decode(bytes);
            offset = 0;
            for (int i = 0; i < labels[offset].length; i++) {
                dsts[offset][i].setText("" + pose.r(i));
            }
            offset += 1;
            for (int i = 0; i < labels[offset].length; i++) {
                dsts[offset][i].setText("" + pose.q(i));
            }

            ByteBuffer bb = poseSerializer.encodeBB(r, q);
            Pose poseBB = poseSerializer.decodeBB(bb);
            offset = 0;
            for (int i = 0; i < labels[offset].length; i++) {
                dstBBs[offset][i].setText("" + poseBB.r(i));
            }
            offset += 1;
            for (int i = 0; i < labels[offset].length; i++) {
                dstBBs[offset][i].setText("" + poseBB.q(i));
            }
        });
        setContentView(vertical);

    }
}
