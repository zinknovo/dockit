package com.javaee.documentservice.versioning;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 文档版本控制配置
 * 控制 git 仓库根目录、默认提交人、文件校验等行为
 */
@Configuration
@ConfigurationProperties(prefix = "dockit.versioning")
public class VersionControlProperties {

    /** git 仓库根目录，每个文档一个子目录 */
    private String root = "/data/dockit-repos";

    /** 默认提交人名称 */
    private String defaultAuthorName = "dockit-system";

    /** 默认提交人邮箱 */
    private String defaultAuthorEmail = "system@dockit.local";

    /** 是否关闭 git core.quotepath（关闭后中文文件名正常显示） */
    private boolean gitQuotepath = false;

    /** 单个文件最大字节数 */
    private long maxFileSize = 10485760L;

    /** 允许上传的文件扩展名 */
    private List<String> allowedExtensions = List.of("txt", "md", "docx", "doc", "pdf");

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public String getDefaultAuthorName() {
        return defaultAuthorName;
    }

    public void setDefaultAuthorName(String defaultAuthorName) {
        this.defaultAuthorName = defaultAuthorName;
    }

    public String getDefaultAuthorEmail() {
        return defaultAuthorEmail;
    }

    public void setDefaultAuthorEmail(String defaultAuthorEmail) {
        this.defaultAuthorEmail = defaultAuthorEmail;
    }

    public boolean isGitQuotepath() {
        return gitQuotepath;
    }

    public void setGitQuotepath(boolean gitQuotepath) {
        this.gitQuotepath = gitQuotepath;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public List<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    public void setAllowedExtensions(List<String> allowedExtensions) {
        this.allowedExtensions = allowedExtensions;
    }
}
