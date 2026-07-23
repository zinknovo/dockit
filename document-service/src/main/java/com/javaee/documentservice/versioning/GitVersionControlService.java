package com.javaee.documentservice.versioning;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 git 的版本控制实现
 * 通过 ProcessBuilder 调用系统 git，每个 scopeId 一个独立仓库。
 * 从 Dockit GitVersionControlService 迁移：异常改用 VersionControlException，
 * init 关闭 core.quotepath，commit 用 scopeId.intern() 保护并发，新增 repoExists。
 */
@Service
public class GitVersionControlService implements VersionControlService {

    private static final Logger log = LoggerFactory.getLogger(GitVersionControlService.class);

    private final VersionControlProperties properties;

    public GitVersionControlService(VersionControlProperties properties) {
        this.properties = properties;
    }

    @Override
    public void initRepo(String scopeId) {
        Path repoPath = repoPath(scopeId);
        try {
            Files.createDirectories(repoPath);
        } catch (IOException e) {
            throw new VersionControlException("无法创建版本仓库目录: " + scopeId, e);
        }
        runGit(repoPath, "init", "-b", "main");
        runGit(repoPath, "config", "user.name", properties.getDefaultAuthorName());
        runGit(repoPath, "config", "user.email", properties.getDefaultAuthorEmail());
        // 关闭 quotepath 后中文文件名正常显示
        runGit(repoPath, "config", "core.quotepath", String.valueOf(properties.isGitQuotepath()));
    }

    @Override
    public boolean repoExists(String scopeId) {
        return Files.isDirectory(repoPath(scopeId).resolve(".git"));
    }

    @Override
    public String commitVersion(CommitRequest request) {
        String scopeId = request.scopeId();
        // 同一仓库串行提交，避免并发 commit 互相覆盖索引
        synchronized (scopeId.intern()) {
            Path repoPath = repoPath(scopeId);
            String relativePath = request.relativePath();
            Path targetFile = repoPath.resolve(relativePath).normalize();
            if (!targetFile.startsWith(repoPath)) {
                throw new VersionControlException("文件路径不合法: " + relativePath);
            }
            try {
                Path parent = targetFile.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.write(targetFile, request.content());
            } catch (IOException e) {
                throw new VersionControlException("写入文档失败: " + relativePath, e);
            }

            runGit(repoPath, "add", "--", relativePath);

            // 内容无变更时不产生新 commit，直接返回当前 HEAD，避免 git commit 报错
            if (!hasStagedChanges(repoPath)) {
                log.warn("内容无变更，跳过提交: scopeId={}, path={}", scopeId, relativePath);
                return runGit(repoPath, "rev-parse", "HEAD").trim();
            }

            Author author = request.author() != null
                    ? request.author()
                    : new Author(properties.getDefaultAuthorName(), properties.getDefaultAuthorEmail());
            runGit(withAuthor(author), repoPath, "commit", "-m", buildCommitMessage(relativePath, request.note()));
            return runGit(repoPath, "rev-parse", "HEAD").trim();
        }
    }

    @Override
    public List<VersionCommit> listHistory(String scopeId, String relativePath) {
        Path repoPath = repoPath(scopeId);
        String output = runGit(repoPath, "log", "--format=%H|%ct", "--", relativePath);
        List<VersionCommit> commits = new ArrayList<>();
        if (output == null || output.isBlank()) {
            return commits;
        }
        for (String line : output.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] pieces = line.split("\\|");
            if (pieces.length < 2) {
                continue;
            }
            commits.add(new VersionCommit(pieces[0], Instant.ofEpochSecond(Long.parseLong(pieces[1]))));
        }
        return commits;
    }

    @Override
    public String readFileAtVersion(String scopeId, String commitHash, String relativePath) {
        Path repoPath = repoPath(scopeId);
        return runGit(repoPath, "show", commitHash + ":" + relativePath);
    }

    private boolean hasStagedChanges(Path repoPath) {
        // git diff --cached --quiet：exit 0 表示无变更，exit 1 表示有变更
        return runGitExitCode(repoPath, "diff", "--cached", "--quiet") != 0;
    }

    private String buildCommitMessage(String relativePath, String note) {
        String normalizedNote = (note == null || note.isBlank()) ? "上传新版本" : note.trim();
        return "更新文件 " + relativePath + " - " + normalizedNote;
    }

    private Map<String, String> withAuthor(Author author) {
        Map<String, String> env = new HashMap<>();
        env.put("GIT_AUTHOR_NAME", author.name());
        env.put("GIT_AUTHOR_EMAIL", author.email());
        env.put("GIT_COMMITTER_NAME", author.name());
        env.put("GIT_COMMITTER_EMAIL", author.email());
        return env;
    }

    private Path repoPath(String scopeId) {
        return Path.of(properties.getRoot()).resolve(scopeId);
    }

    private String runGit(Path repoPath, String... command) {
        return runGit(new HashMap<>(), repoPath, command);
    }

    private String runGit(Map<String, String> environment, Path repoPath, String... command) {
        List<String> fullCommand = new ArrayList<>();
        fullCommand.add("git");
        fullCommand.addAll(Arrays.asList(command));

        ProcessBuilder processBuilder = new ProcessBuilder(fullCommand);
        processBuilder.directory(repoPath.toFile());
        processBuilder.environment().putAll(environment);

        try {
            Process process = processBuilder.start();
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            process.getInputStream().transferTo(stdout);
            process.getErrorStream().transferTo(stderr);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new VersionControlException(stderr.toString(StandardCharsets.UTF_8).trim());
            }
            return stdout.toString(StandardCharsets.UTF_8);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VersionControlException("执行版本命令失败", e);
        }
    }

    private int runGitExitCode(Path repoPath, String... command) {
        List<String> fullCommand = new ArrayList<>();
        fullCommand.add("git");
        fullCommand.addAll(Arrays.asList(command));

        ProcessBuilder processBuilder = new ProcessBuilder(fullCommand);
        processBuilder.directory(repoPath.toFile());

        try {
            Process process = processBuilder.start();
            process.getInputStream().transferTo(OutputStream.nullOutputStream());
            process.getErrorStream().transferTo(OutputStream.nullOutputStream());
            return process.waitFor();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VersionControlException("执行版本命令失败", e);
        }
    }
}
