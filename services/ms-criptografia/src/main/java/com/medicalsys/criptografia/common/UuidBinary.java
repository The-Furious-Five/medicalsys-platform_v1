package com.medicalsys.criptografia.common;

import java.nio.ByteBuffer;
import java.util.UUID;

public final class UuidBinary {

    private UuidBinary() {
    }

    public static byte[] toBytes(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    public static UUID fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length != 16) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    public static UUID parseFlexible(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim())) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() == 32) {
            return UUID.fromString(
                    trimmed.substring(0, 8) + "-"
                            + trimmed.substring(8, 12) + "-"
                            + trimmed.substring(12, 16) + "-"
                            + trimmed.substring(16, 20) + "-"
                            + trimmed.substring(20, 32));
        }
        return UUID.fromString(trimmed);
    }
}
