package com.javaee.documentservice.service;

/**
 * 文档原始文件存储服务
 * 负责把上传的原始文件（docx/pdf 等）存入 MinIO，并生成 presigned 下载链接供 doc-parser 拉取。
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
     * 生成 presigned GET URL，供 doc-parser 下载该文件
     */
    String getPresignedDownloadUrl(String bucketName, String objectKey);
}
