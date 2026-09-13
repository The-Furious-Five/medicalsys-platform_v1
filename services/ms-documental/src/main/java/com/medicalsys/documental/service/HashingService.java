package com.medicalsys.documental.service;

import com.medicalsys.documental.exception.ApiException;
import com.medicalsys.documental.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class HashingService {

    public String sha256(InputStream input) {
        MessageDigest digest = sha256Digest();
        byte[] buffer = new byte[8192];
        int read;
        try {
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "No fue posible calcular la integridad del archivo", exception);
        }
    }

    public String copyAndSha256(InputStream input, OutputStream output) {
        MessageDigest digest = sha256Digest();
        byte[] buffer = new byte[8192];
        int read;
        try {
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException exception) {
            throw new ApiException(ErrorCode.STORAGE_UNAVAILABLE,
                    "No fue posible preparar el archivo para descarga", exception);
        }
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no esta disponible en la JVM", exception);
        }
    }
}
