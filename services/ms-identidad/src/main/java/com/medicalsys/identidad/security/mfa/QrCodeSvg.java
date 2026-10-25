package com.medicalsys.identidad.security.mfa;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * QR de la URI otpauth como SVG en una data URI. Se genera en el servidor para que el secreto no pase
 * por ninguna librería ni servicio de terceros en el navegador; la CSP del frontend ya admite
 * {@code img-src data:}.
 */
public final class QrCodeSvg {

    private static final int QUIET_ZONE = 4;

    private QrCodeSvg() {
    }

    public static String dataUri(String content) {
        BitMatrix matrix;
        try {
            matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 0));
        } catch (WriterException ex) {
            throw new IllegalStateException("No se pudo generar el QR", ex);
        }
        int size = matrix.getWidth() + 2 * QUIET_ZONE;
        StringBuilder path = new StringBuilder();
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                if (matrix.get(x, y)) {
                    path.append('M').append(x + QUIET_ZONE).append(' ').append(y + QUIET_ZONE).append("h1v1h-1z");
                }
            }
        }
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + size + " " + size
                + "\" shape-rendering=\"crispEdges\"><rect width=\"100%\" height=\"100%\" fill=\"#fff\"/>"
                + "<path fill=\"#000\" d=\"" + path + "\"/></svg>";
        return "data:image/svg+xml;base64,"
                + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
    }
}
