package com.javaee.documentservice.service.impl;

import com.javaee.common.exception.BusinessException;
import com.javaee.documentservice.security.BucketPermissionService;
import com.javaee.documentservice.service.DocumentFileStorageService;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.concurrent.TimeUnit;

/**
 * 文档原始文件存储实现，基于 MinIO
 */
@Service
public class DocumentFileStorageServiceImpl implements DocumentFileStorageService {

    private static final Logger log = LoggerFactory.getLogger(DocumentFileStorageServiceImpl.class);

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private BucketPermissionService bucketPermissionService;

    /** presigned URL 有效期（秒），默认 1 小时 */
    @Value("${dockit.storage.presigned-url-expiry-seconds:3600}")
    private long presignedUrlExpirySeconds;

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
    public String getPresignedDownloadUrl(String bucketName, String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucketName)
                    .object(objectKey)
                    .expiry((int) presignedUrlExpirySeconds, TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            log.error("生成 presigned 下载链接失败, bucket={}, key={}", bucketName, objectKey, e);
            throw new BusinessException("生成下载链接失败: " + e.getMessage());
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
