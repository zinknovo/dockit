package com.javaee.documentservice.service;

/**
 * 文档原始文件存储服务
 * 负责把上传的原始文件（docx/pdf 等）存入 MinIO。
 * 与 {@link DocumentContentService}（文本内容存储）职责分离。
 */
public interface DocumentFileStorageService {

    /**
     * 上传原始文件到 MinIO
     *
     * @param bucketName  目标桶
     * @param objectKey   对象 key
     * @param content     文件字节
     * @param contentType MIME 类型
     * @return 实际写入的 objectKey
     */
    String saveFile(String bucketName, String objectKey, byte[] content, String contentType);

    /**
     * 读取 MinIO 中的原始文件字节
     *
     * @param bucketName 桶名
     * @param objectKey  对象 key
     * @return 文件字节
     */
    byte[] readFile(String bucketName, String objectKey);
}
