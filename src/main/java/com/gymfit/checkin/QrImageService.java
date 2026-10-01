package com.gymfit.checkin;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

@Service
public class QrImageService {

    public byte[] createPng(String value) {
        try {
            BitMatrix matrix =
                    new MultiFormatWriter()
                            .encode(
                                    value,
                                    BarcodeFormat.QR_CODE,
                                    320,
                                    320
                            );

            ByteArrayOutputStream output =
                    new ByteArrayOutputStream();

            MatrixToImageWriter.writeToStream(
                    matrix,
                    "PNG",
                    output
            );

            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Cannot create QR image",
                    exception
            );
        }
    }
}