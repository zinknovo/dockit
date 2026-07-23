package com.javaee.documentservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档版本实体类
 */
@Data
@TableName("document_version")
public class DocumentVersion {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String documentId;

    private Integer versionNumber;

    private String title;

    private String content;

    private String summary;

    private String keywords;

    private String changeLog;

    /** git commit hash */
    private String commitHash;

    /** git 仓库内相对路径 */
    private String filePath;

    /** 用户备注（可修改，与 changeLog 区分） */
    private String note;

    /** 上传人 */
    private String uploadedBy;

    /** 上传时间 */
    private LocalDateTime uploadedAt;

    /** MinIO 对象 key，diff 时生成 presigned URL 供 doc-parser 下载 */
    private String fileUrl;

    private String createdBy;

    private LocalDateTime createTime;
}
