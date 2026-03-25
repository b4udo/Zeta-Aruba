package com.zeta.pec.storage;

import com.zeta.pec.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentStorageService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    @PostConstruct
    void initializeBucket() {
        try {
            ensureBucketExists(minioProperties.getBucket());
        } catch (Exception ex) {
            log.warn("MinIO bucket initialization skipped: {}", ex.getMessage());
        }
    }

    public String uploadDocument(String bucket, String objectKey, InputStream data, String contentType) {
        try {
            ensureBucketExists(bucket);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(data, -1, 10 * 1024 * 1024)
                    .contentType(contentType)
                    .build());
            return objectKey;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to upload document to MinIO", ex);
        }
    }

    public InputStream downloadDocument(String bucket, String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to download document from MinIO", ex);
        }
    }

    public void deleteDocument(String bucket, String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to delete document from MinIO", ex);
        }
    }

    private void ensureBucketExists(String bucket) throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }
}
