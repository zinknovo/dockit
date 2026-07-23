package com.javaee.documentservice.versioning;

import java.util.List;

/**
 * 文档版本控制服务接口
 * 每个文档对应一个 git 仓库（scopeId 通常等于文档 id），上传新版本时 commit，
 * 可列出时间线、取任意版本内容。
 */
public interface VersionControlService {

    /**
     * 初始化某 scope 的 git 仓库（已存在则跳过初始化逻辑）
     */
    void initRepo(String scopeId);

    /**
     * 提交一个新版本，返回 commit hash
     */
    String commitVersion(CommitRequest request);

    /**
     * 列出某文件的历史提交（按时间倒序）
     */
    List<VersionCommit> listHistory(String scopeId, String relativePath);

    /**
     * 读取某版本下指定文件的内容
     */
    String readFileAtVersion(String scopeId, String commitHash, String relativePath);

    /**
     * 判断某 scope 的 git 仓库是否已初始化
     */
    boolean repoExists(String scopeId);

    /**
     * 提交请求
     *
     * @param scopeId      仓库 scope（通常等于文档 id）
     * @param relativePath 仓库内相对路径
     * @param content      文件字节内容
     * @param author       提交作者，为 null 时用默认系统作者
     * @param note         提交备注
     */
    record CommitRequest(String scopeId, String relativePath, byte[] content, Author author, String note) {
    }
}
