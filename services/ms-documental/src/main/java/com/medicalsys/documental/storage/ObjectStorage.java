package com.medicalsys.documental.storage;

import java.io.InputStream;
import java.util.Map;

public interface ObjectStorage {

    StoredObject put(String objectKey, InputStream input, long size, String contentType,
            Map<String, String> metadata);

    InputStream get(String objectKey);

    void delete(String objectKey);

    boolean bucketAvailable();

    record StoredObject(String versionId) {
    }
}
