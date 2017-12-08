package io.jeti.measure.remote;

import android.content.Intent;
import com.google.zxing.Result;
import io.jeti.measure.utils.QRScannerActivity;

public class GetClientAddressActivity extends QRScannerActivity {

    public static final String SCAN_RESULT = "SCAN_RESULT";

    @Override
    public void handleResult(Result rawResult) {
        Intent intent = new Intent(this, io.jeti.measure.remote.CaptureCommandActivity.class);
        intent.putExtra(SCAN_RESULT, rawResult.getText());
        startActivity(intent);
    }

    @Override
    public String getHeader() {
        return "Image Source";
    }
}