package com.javaee.documentservice.service.impl;

import com.javaee.common.exception.BusinessException;
import com.javaee.documentservice.security.BucketPermissionService;
import com.javaee.documentservice.service.DocumentFileStorageService;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * 文档原始文件存储实现，基于 MinIO
 */
@Service
public class DocumentFileStorageServiceImpl implements DocumentFileStorageService {

    private static final Logger log = LoggerFactory.getLogger(DocumentFileStorageServiceImpl.class);

    private final MinioClient minioClient;

    private final BucketPermissionService bucketPermissionService;

    @Autowired
    public DocumentFileStorageServiceImpl(MinioClient minioClient, BucketPermissionService bucketPermissionService) {
        this.minioClient = minioClient;
        this.bucketPermissionService = bucketPermissionService;
    }

    @Override
    public String saveFile(String bucketName, String objectKey, byte[] content, String contentType) {
        bucketPermissionService.assertCanAccess(bucketName);
        try {
            ensureBucketExists(bucketName);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
            log.info("文件已上传到 MinIO, bucket={}, key={}, size={}", bucketName, objectKey, content.length);
            return objectKey;
        } catch (Exception e) {
            log.error("文件上传 MinIO 失败, bucket={}, key={}", bucketName, objectKey, e);
            throw new BusinessException("文件上传失败: " + e.getMessage());
        }
    }

    @Override
    public byte[] readFile(String bucketName, String objectKey) {
        try {
            InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .build());
            try (stream) {
                return stream.readAllBytes();
            }
        } catch (Exception e) {
            log.error("读取文件失败, bucket={}, key={}", bucketName, objectKey, e);
            throw new BusinessException("读取文件失败: " + e.getMessage());
        }
    }

    private void ensureBucketExists(String bucketName) throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        if (!exists) {
            log.info("Bucket {} 不存在，自动创建", bucketName);
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }
}
