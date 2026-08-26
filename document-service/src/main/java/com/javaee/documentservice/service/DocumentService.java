package com.javaee.documentservice.service;

import com.javaee.documentservice.dto.DocumentCreateDTO;
import com.javaee.documentservice.dto.DocumentQueryDTO;
import com.javaee.documentservice.dto.DocumentUpdateDTO;
import com.javaee.documentservice.entity.DocumentVersion;
import com.javaee.documentservice.vo.DocumentVO;
import com.javaee.documentservice.vo.DocumentVersionVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档服务接口
 * 提供文档的创建、更新、删除、查询、版本控制等核心业务功能
 */
public interface DocumentService {

    /**
     * 创建文档
     * @param dto 创建文档请求参数
     * @param userId 创建用户ID
     * @return 文档VO
     */
    DocumentVO create(DocumentCreateDTO dto, Long userId);

    /**
     * 更新文档
     * @param id 文档ID
     * @param dto 更新文档请求参数
     * @param userId 更新用户ID
     * @return 更新后的文档VO
     */
    DocumentVO update(String id, DocumentUpdateDTO dto, Long userId);

    /**
     * 删除文档（软删除）
     * @param id 文档ID
     * @param userId 删除用户ID
     */
    void delete(String id, Long userId);

    /**
     * 授权用户协作访问文档。
     */
    void grantAccess(String id, Long collaboratorUserId, String role, Long operatorUserId);

    /**
     * 根据ID获取文档详情
     * @param id 文档ID
     * @return 文档VO
     */
    DocumentVO getById(String id, Long userId);

    /**
     * 获取文档在MinIO中的存储位置。
     */
    DocumentVO getStorageLocation(String id, Long userId);

    /**
     * 获取用户的文档列表
     * @param userId 用户ID
     * @return 文档VO列表
     */
    List<DocumentVO> getByUserId(Long userId);

    /**
     * 搜索文档
     * @param dto 查询参数（支持关键词、分类）
     * @return 文档VO列表
     */
    List<DocumentVO> search(DocumentQueryDTO dto, Long userId);

    /**
     * 获取文档的所有版本
     * @param documentId 文档ID
     * @return 文档版本列表
     */
    List<DocumentVersion> getVersions(String documentId, Long userId);

    /**
     * 恢复文档到指定版本
     * @param documentId 文档ID
     * @param versionNumber 版本号
     * @param userId 操作用户ID
     * @return 恢复后的文档VO
     */
    DocumentVO restoreVersion(String documentId, Integer versionNumber, Long userId);

    /**
     * 上传文档新版本（基于 git 版本控制 + MinIO 原始文件存储）
     *
     * @param documentId 文档ID
     * @param file       上传的文件
     * @param note       版本备注
     * @param userId     操作用户ID
     * @return 新版本VO
     */
    DocumentVersionVO uploadNewVersion(String documentId, MultipartFile file, String note, Long userId);

    /**
     * 获取文档版本列表（按版本号倒序）
     *
     * @param documentId 文档ID
     * @param userId     操作用户ID
     * @return 版本VO列表
     */
    List<DocumentVersionVO> listVersions(String documentId, Long userId);

    /**
     * 获取某个版本的详情（元数据，不含文件内容）
     *
     * @param documentId 文档ID
     * @param versionId  版本ID
     * @param userId     操作用户ID
     * @return 版本VO
     */
    DocumentVersionVO getVersionDetail(String documentId, String versionId, Long userId);

    /**
     * 读取某个版本的文件内容（原始文件字节）
     *
     * @param documentId 文档ID
     * @param versionId  版本ID
     * @param userId     操作用户ID
     * @return 文件字节
     */
    byte[] getVersionContent(String documentId, String versionId, Long userId);

    /**
     * 修改版本备注
     *
     * @param versionId 版本ID
     * @param note      新备注
     * @param userId    操作用户ID
     */
    void updateVersionNote(String versionId, String note, Long userId);

}
