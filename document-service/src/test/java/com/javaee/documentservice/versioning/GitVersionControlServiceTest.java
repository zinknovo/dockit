package com.javaee.documentservice.versioning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GitVersionControlService 集成测试，跑真实 git（需系统 PATH 上有 git）。
 */
@EnabledIf("gitAvailable")
class GitVersionControlServiceTest {

    /** 仅在系统 PATH 上存在 git 时运行，避免无 git 环境下误报失败 */
    static boolean gitAvailable() {
        try {
            Process process = new ProcessBuilder("git", "--version").redirectErrorStream(true).start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private GitVersionControlService service;
    private VersionControlProperties properties;

    @TempDir
    Path tempRoot;

    @BeforeEach
    void setUp() {
        properties = new VersionControlProperties();
        properties.setRoot(tempRoot.toString());
        service = new GitVersionControlService(properties);
    }

    @Test
    void initRepoCreatesGitDirectoryAndRepoExistsReflectsState() {
        assertThat(service.repoExists("doc-1")).isFalse();

        service.initRepo("doc-1");

        assertThat(service.repoExists("doc-1")).isTrue();
        assertThat(Files.isDirectory(tempRoot.resolve("doc-1").resolve(".git"))).isTrue();
    }

    @Test
    void commitVersionReturnsHeadHashAndRecordsHistory() {
        service.initRepo("doc-1");
        String path = "doc-1/contract.md";

        String hash = service.commitVersion(req("doc-1", path, "hello".getBytes()));

        assertThat(hash).isNotBlank();

        List<VersionCommit> history = service.listHistory("doc-1", path);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).commitHash()).isEqualTo(hash);
        assertThat(history.get(0).committedAt()).isNotNull();
    }

    @Test
    void readFileAtVersionReturnsContentForEachCommit() {
        service.initRepo("doc-1");
        String path = "doc-1/contract.md";

        String v1 = service.commitVersion(req("doc-1", path, "version1".getBytes()));
        service.commitVersion(req("doc-1", path, "version2".getBytes()));

        assertThat(service.readFileAtVersion("doc-1", v1, path)).isEqualTo("version1");
        assertThat(service.readFileAtVersion("doc-1", "HEAD", path)).isEqualTo("version2");
        assertThat(service.listHistory("doc-1", path)).hasSize(2);
    }

    @Test
    void commitVersionRejectsPathTraversal() {
        service.initRepo("doc-1");

        assertThatThrownBy(() -> service.commitVersion(req("doc-1", "../escape.txt", "x".getBytes())))
                .isInstanceOf(VersionControlException.class)
                .hasMessageContaining("文件路径不合法");
    }

    @Test
    void commitVersionSupportsChineseFilenameAndContent() {
        service.initRepo("doc-1");
        String path = "文档/合同.md";

        String hash = service.commitVersion(req("doc-1", path, "中文内容".getBytes()));

        assertThat(service.readFileAtVersion("doc-1", hash, path)).isEqualTo("中文内容");
    }

    @Test
    void commitVersionWithSameContentDoesNotCreateNewCommit() {
        service.initRepo("doc-1");
        String path = "doc-1/a.md";

        String v1 = service.commitVersion(req("doc-1", path, "same".getBytes()));
        String v2 = service.commitVersion(req("doc-1", path, "same".getBytes()));

        assertThat(v2).isEqualTo(v1);
        assertThat(service.listHistory("doc-1", path)).hasSize(1);
    }

    private VersionControlService.CommitRequest req(String scopeId, String path, byte[] content) {
        return new VersionControlService.CommitRequest(scopeId, path, content, null, "备注");
    }
}
