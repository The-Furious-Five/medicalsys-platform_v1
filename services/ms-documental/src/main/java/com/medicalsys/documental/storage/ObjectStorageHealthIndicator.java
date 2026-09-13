package com.medicalsys.documental.storage;

import com.medicalsys.documental.config.StorageProperties;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("minio")
public class ObjectStorageHealthIndicator implements HealthIndicator {

    private final ObjectStorage storage;
    private final StorageProperties properties;

    public ObjectStorageHealthIndicator(ObjectStorage storage, StorageProperties properties) {
        this.storage = storage;
        this.properties = properties;
    }

    @Override
    public Health health() {
        if (storage.bucketAvailable()) {
            return Health.up().withDetail("bucket", properties.bucket()).build();
        }
        return Health.down().withDetail("bucket", properties.bucket()).build();
    }
}
