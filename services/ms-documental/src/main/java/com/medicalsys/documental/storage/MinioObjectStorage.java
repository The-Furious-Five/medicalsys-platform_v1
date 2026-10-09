package com.medicalsys.documental.storage;

import com.medicalsys.documental.config.StorageProperties;
import com.medicalsys.documental.exception.ApiException;
import com.medicalsys.documental.exception.ErrorCode;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.InputStream;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MinioObjectStorage implements ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(MinioObjectStorage.class);

    private final MinioClient client;
    private final StorageProperties properties;

    public MinioObjectStorage(MinioClient client, StorageProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public StoredObject put(String objectKey, InputStream input, long size, String contentType,
            Map<String, String> metadata) {
        try {
            ensureBucket();
            ObjectWriteResponse response = client.putObject(PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .stream(input, size, -1L)
                    .contentType(contentType)
                    .userMetadata(metadata)
                    .build());
            return new StoredObject(response.versionId());
        } catch (Exception exception) {
            throw unavailable("No fue posible almacenar el archivo en MinIO", exception);
        }
    }

    @Override
    public InputStream get(String objectKey) {
        try {
            return client.getObject(GetObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .build());
        } catch (Exception exception) {
            throw unavailable("No fue posible recuperar el archivo desde MinIO", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .build());
        } catch (Exception exception) {
            log.error("No se pudo compensar el objeto MinIO key={}", objectKey, exception);
        }
    }

    @Override
    public boolean bucketAvailable() {
        try {
            return client.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket()).build());
        } catch (Exception exception) {
            return false;
        }
    }

    private void ensureBucket() throws Exception {
        boolean exists = client.bucketExists(BucketExistsArgs.builder()
                .bucket(properties.bucket())
                .build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
        }
    }

    private ApiException unavailable(String message, Exception cause) {
        return new ApiException(ErrorCode.STORAGE_UNAVAILABLE, message, cause);
    }
}
