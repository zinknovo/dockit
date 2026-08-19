package com.javaee.documentservice.controller;

import com.javaee.common.model.Result;
import com.javaee.documentservice.client.dto.ContractCompareResponse;
import com.javaee.documentservice.dto.DocumentCreateDTO;
import com.javaee.documentservice.dto.DocumentQueryDTO;
import com.javaee.documentservice.dto.DocumentUpdateDTO;
import com.javaee.documentservice.dto.VersionDiffRequest;
import com.javaee.documentservice.security.RequestUserContext;
import com.javaee.documentservice.service.DocumentService;
import com.javaee.documentservice.vo.DocumentVO;
import com.javaee.documentservice.vo.DocumentVersionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档管理控制器
 * 提供文档的CRUD和版本控制REST API接口
 */
@RestController
@RequestMapping("/api/documents")
@Tag(name = "文档管理", description = "文档创建、更新、删除、查询、版本控制等接口")
public class DocumentController {

    private final DocumentService documentService;

    private final RequestUserContext requestUserContext;

    @Autowired
    public DocumentController(DocumentService documentService, RequestUserContext requestUserContext) {
        this.documentService = documentService;
        this.requestUserContext = requestUserContext;
    }

    /**
     * 创建文档
     * @param dto 创建文档请求参数
     * @return 文档VO
     */
    @PostMapping
    @Operation(summary = "创建文档", description = "创建新文档，自动保存初始版本")
    public Result<DocumentVO> create(@RequestBody DocumentCreateDTO dto) {
        Long userId = requestUserContext.getRequiredUserId();
        DocumentVO document = documentService.create(dto, userId);
        return Result.success(document);
    }

    /**
     * 更新文档
     * @param id 文档ID
     * @param dto 更新文档请求参数
     * @return 更新后的文档VO
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新文档", description = "更新文档内容，自动保存历史版本")
    public Result<DocumentVO> update(
            @Parameter(description = "文档ID") @PathVariable String id,
            @RequestBody DocumentUpdateDTO dto) {
        Long userId = requestUserContext.getRequiredUserId();
        DocumentVO document = documentService.update(id, dto, userId);
        return Result.success(document);
    }

    /**
     * 删除文档
     * @param id 文档ID
     * @return 无
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除文档", description = "软删除文档，将文档状态标记为已删除")
    public Result<Void> delete(@Parameter(description = "文档ID") @PathVariable String id) {
        Long userId = requestUserContext.getRequiredUserId();
        documentService.delete(id, userId);
        return Result.success();
    }

    /**
     * 添加文档协作者
     * @param id 文档ID
     * @param collaboratorUserId 协作者用户ID
     * @param role 协作角色
     * @return 无
     */
    @PostMapping("/{id}/collaborators/{collaboratorUserId}")
    @Operation(summary = "添加文档协作者", description = "为文档授权协作者，角色支持 owner/editor/viewer")
    public Result<Void> grantAccess(
            @Parameter(description = "文档ID") @PathVariable String id,
            @Parameter(description = "协作者用户ID") @PathVariable Long collaboratorUserId,
            @Parameter(description = "协作角色") @RequestParam(defaultValue = "editor") String role) {
        Long userId = requestUserContext.getRequiredUserId();
        documentService.grantAccess(id, collaboratorUserId, role, userId);
        return Result.success();
    }

    /**
     * 获取文档详情
     * @param id 文档ID
     * @return 文档VO
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取文档详情", description = "根据文档ID获取文档详细信息")
    public Result<DocumentVO> getById(@Parameter(description = "文档ID") @PathVariable String id) {
        Long userId = requestUserContext.getRequiredUserId();
        DocumentVO document = documentService.getById(id, userId);
        return Result.success(document);
    }

    /**
     * 获取文档MinIO存储位置
     * @param id 文档ID
     * @return 文档存储位置
     */
    @GetMapping("/{id}/storage")
    @Operation(summary = "获取文档存储位置", description = "根据文档ID获取其MinIO bucket/objectName，供AI文件操作使用")
    public Result<DocumentVO> getStorageLocation(@Parameter(description = "文档ID") @PathVariable String id) {
        Long userId = requestUserContext.getRequiredUserId();
        DocumentVO document = documentService.getStorageLocation(id, userId);
        return Result.success(document);
    }

    /**
     * 获取用户文档列表
     * @param userId 用户ID
     * @return 文档VO列表
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "获取用户文档列表", description = "获取指定用户的所有活跃文档列表")
    public Result<List<DocumentVO>> getByUserId(@Parameter(description = "用户ID") @PathVariable Long userId) {
        Long currentUserId = requestUserContext.getRequiredUserId();
        if (!currentUserId.equals(userId)) {
            throw new com.javaee.common.exception.BusinessException("无权查看其他用户的文档");
        }
        List<DocumentVO> documents = documentService.getByUserId(currentUserId);
        return Result.success(documents);
    }

    /**
     * 搜索文档
     * @param keyword 关键词（可选）
     * @param category 分类（可选）
     * @return 文档VO列表
     */
    @GetMapping("/search")
    @Operation(summary = "搜索文档", description = "根据关键词搜索标题、内容、关键词，或按分类筛选文档")
    public Result<List<DocumentVO>> search(
            @Parameter(description = "关键词（搜索标题、内容、关键词）") @RequestParam(required = false) String keyword,
            @Parameter(description = "分类") @RequestParam(required = false) String category) {
        DocumentQueryDTO dto = new DocumentQueryDTO();
        dto.setKeyword(keyword);
        dto.setCategory(category);
        Long userId = requestUserContext.getRequiredUserId();
        List<DocumentVO> documents = documentService.search(dto, userId);
        return Result.success(documents);
    }

    /**
     * 获取文档版本列表
     * @param id 文档ID
     * @return 版本VO列表
     */
    @GetMapping("/{id}/versions")
    @Operation(summary = "获取文档版本列表", description = "获取文档的所有历史版本，按版本号降序排列")
    public Result<List<DocumentVersionVO>> getVersions(@Parameter(description = "文档ID") @PathVariable String id) {
        Long userId = requestUserContext.getRequiredUserId();
        return Result.success(documentService.listVersions(id, userId));
    }

    /**
     * 上传文档新版本
     * @param id 文档ID
     * @param file 上传的文件
     * @param note 版本备注
     * @return 新版本VO
     */
    @PostMapping("/{id}/versions")
    @Operation(summary = "上传文档新版本", description = "上传文件作为文档新版本，写入 git 并存储到 MinIO")
    public Result<DocumentVersionVO> uploadNewVersion(
            @Parameter(description = "文档ID") @PathVariable String id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "note", required = false) String note) {
        Long userId = requestUserContext.getRequiredUserId();
        return Result.success(documentService.uploadNewVersion(id, file, note, userId));
    }

    /**
     * 获取版本详情
     * @param id 文档ID
     * @param versionId 版本ID
     * @return 版本VO
     */
    @GetMapping("/{id}/versions/{versionId}")
    @Operation(summary = "获取版本详情", description = "获取文档某个版本的元数据")
    public Result<DocumentVersionVO> getVersionDetail(
            @Parameter(description = "文档ID") @PathVariable String id,
            @Parameter(description = "版本ID") @PathVariable String versionId) {
        Long userId = requestUserContext.getRequiredUserId();
        return Result.success(documentService.getVersionDetail(id, versionId, userId));
    }

    /**
     * 获取版本文件内容
     * @param id 文档ID
     * @param versionId 版本ID
     * @return 文件内容
     */
    @GetMapping("/{id}/versions/{versionId}/content")
    @Operation(summary = "获取版本文件内容", description = "读取文档某个版本的文件内容（从 git 历史中取）")
    public Result<String> getVersionContent(
            @Parameter(description = "文档ID") @PathVariable String id,
            @Parameter(description = "版本ID") @PathVariable String versionId) {
        Long userId = requestUserContext.getRequiredUserId();
        return Result.success(documentService.getVersionContent(id, versionId, userId));
    }

    /**
     * 修改版本备注
     * @param id 文档ID
     * @param versionId 版本ID
     * @param note 新备注
     * @return 无
     */
    @PutMapping("/{id}/versions/{versionId}/note")
    @Operation(summary = "修改版本备注", description = "更新某个版本的备注")
    public Result<Void> updateVersionNote(
            @Parameter(description = "文档ID") @PathVariable String id,
            @Parameter(description = "版本ID") @PathVariable String versionId,
            @Parameter(description = "新备注") @RequestParam("note") String note) {
        Long userId = requestUserContext.getRequiredUserId();
        documentService.updateVersionNote(versionId, note, userId);
        return Result.success();
    }

    /**
     * 比对两个版本
     * @param id 文档ID
     * @param request 比对请求（fromVersionId / toVersionId）
     * @return 比对结果
     */
    @PostMapping("/{id}/diff")
    @Operation(summary = "比对两个版本", description = "调用 doc-parser 比对文档两个版本的差异")
    public Result<ContractCompareResponse> diffVersions(
            @Parameter(description = "文档ID") @PathVariable String id,
            @RequestBody VersionDiffRequest request) {
        Long userId = requestUserContext.getRequiredUserId();
        return Result.success(documentService.diffVersions(id, request.fromVersionId(), request.toVersionId(), userId));
    }

    /**
     * 恢复文档版本
     * @param id 文档ID
     * @param versionNumber 版本号
     * @return 恢复后的文档VO
     */
    @PostMapping("/{id}/restore/{versionNumber}")
    @Operation(summary = "恢复文档版本", description = "将文档恢复到指定版本，自动保存当前版本作为历史版本")
    public Result<DocumentVO> restoreVersion(
            @Parameter(description = "文档ID") @PathVariable String id,
            @Parameter(description = "版本号") @PathVariable Integer versionNumber) {
        Long userId = requestUserContext.getRequiredUserId();
        DocumentVO document = documentService.restoreVersion(id, versionNumber, userId);
        return Result.success(document);
    }
}
