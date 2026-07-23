package com.javaee.documentservice.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档版本VO
 */
@Data
public class DocumentVersionVO {

    private String id;

    private String documentId;

    private Integer versionNumber;

    private String title;

    private String content;

    private String summary;

    private List<String> keywords;

    private String changeLog;

    /** git commit hash */
    private String commitHash;

    /** git 仓库内相对路径 */
    private String filePath;

    /** 用户备注 */
    private String note;

    /** 上传人 */
    private String uploadedBy;

    /** 上传时间 */
    private LocalDateTime uploadedAt;

    /** MinIO 对象 key */
    private String fileUrl;

    private String createdBy;

    private LocalDateTime createTime;
}
