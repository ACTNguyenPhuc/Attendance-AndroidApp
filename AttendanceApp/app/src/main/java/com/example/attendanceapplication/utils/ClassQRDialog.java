package com.example.attendanceapplication.utils;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.attendanceapplication.R;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * ClassQRDialog shows a QR code that students scan to join a class.
 * The QR payload: {"classId":"...", "type":"join"}
 */
public class ClassQRDialog {

    public static void show(Context context, String classId, String className) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_class_qr);

        ImageView ivQr = dialog.findViewById(R.id.iv_qr);
        TextView tvLabel = dialog.findViewById(R.id.tv_label);
        TextView tvClose = dialog.findViewById(R.id.tv_close);

        if (tvLabel != null) {
            tvLabel.setText("Cho sinh viên quét để vào lớp:\n" + className);
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("classId", classId);
            payload.put("type", "join");
            Bitmap qr = AttendanceUtils.generateQRCode(payload.toString(), 600);
            if (ivQr != null && qr != null) ivQr.setImageBitmap(qr);
        } catch (JSONException ignored) {}

        if (tvClose != null) tvClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}
