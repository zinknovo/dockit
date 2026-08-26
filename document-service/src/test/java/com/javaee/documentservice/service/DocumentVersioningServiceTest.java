package com.javaee.documentservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javaee.common.exception.BusinessException;
import com.javaee.documentservice.entity.Document;
import com.javaee.documentservice.entity.DocumentVersion;
import com.javaee.documentservice.mapper.DocumentMapper;
import com.javaee.documentservice.mapper.DocumentVersionMapper;
import com.javaee.documentservice.service.impl.DocumentServiceImpl;
import com.javaee.documentservice.vo.DocumentVersionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档版本控制业务逻辑单元测试
 * mock 掉 DocumentFileStorageService / mapper，
 * 覆盖上传、列版本、取内容、恢复、改备注主链路
 */
class DocumentVersioningServiceTest {

    private DocumentMapper documentMapper;
    private DocumentVersionMapper documentVersionMapper;
    private DocumentContentService documentContentService;
    private DocumentAccessService documentAccessService;
    private DocumentFileStorageService documentFileStorageService;
    private DocumentServiceImpl documentService;

    @BeforeEach
    void setUp() {
        documentMapper = org.mockito.Mockito.mock(DocumentMapper.class);
        documentVersionMapper = org.mockito.Mockito.mock(DocumentVersionMapper.class);
        documentContentService = org.mockito.Mockito.mock(DocumentContentService.class);
        documentAccessService = org.mockito.Mockito.mock(DocumentAccessService.class);
        documentFileStorageService = org.mockito.Mockito.mock(DocumentFileStorageService.class);

        documentService = new DocumentServiceImpl(null, null, null, null, null, null, null);
        ReflectionTestUtils.setField(documentService, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(documentService, "documentVersionMapper", documentVersionMapper);
        ReflectionTestUtils.setField(documentService, "documentContentService", documentContentService);
        ReflectionTestUtils.setField(documentService, "documentAccessService", documentAccessService);
        ReflectionTestUtils.setField(documentService, "documentFileStorageService", documentFileStorageService);
        ReflectionTestUtils.setField(documentService, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(documentService, "maxFileSize", 10485760L);
        ReflectionTestUtils.setField(documentService, "allowedExtensions", "txt,md,docx,doc,pdf");
    }

    @Test
    void uploadNewVersionStoresFileAndRecordsVersion() {
        Document document = newDocument("doc-1", 7L);
        document.setVersion(0);
        when(documentMapper.selectById("doc-1")).thenReturn(document);
        when(documentContentService.getBucketName(7L)).thenReturn("user-7");
        when(documentVersionMapper.selectMaxVersionNumber("doc-1")).thenReturn(0);
        when(documentFileStorageService.saveFile(eq("user-7"), anyString(), any(), anyString()))
                .thenReturn("document-files/doc-1/v1/合同.md");
        MockMultipartFile file = new MockMultipartFile("file", "合同.md", "text/markdown", "hello".getBytes());

        DocumentVersionVO vo = documentService.uploadNewVersion("doc-1", file, "初版", 7L);

        assertThat(vo.getVersionNumber()).isEqualTo(1);
        assertThat(vo.getNote()).isEqualTo("初版");
        assertThat(vo.getFileUrl()).isEqualTo("document-files/doc-1/v1/合同.md");

        ArgumentCaptor<DocumentVersion> versionCaptor = ArgumentCaptor.forClass(DocumentVersion.class);
        verify(documentVersionMapper).insert(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getVersionNumber()).isEqualTo(1);
        assertThat(versionCaptor.getValue().getFileUrl()).isEqualTo("document-files/doc-1/v1/合同.md");
        assertThat(versionCaptor.getValue().getUploadedBy()).isEqualTo("7");

        ArgumentCaptor<Document> docCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentMapper).updateById(docCaptor.capture());
        assertThat(docCaptor.getValue().getVersion()).isEqualTo(1);
    }

    @Test
    void uploadNewVersionRejectsUnsupportedExtension() {
        Document document = newDocument("doc-1", 7L);
        when(documentMapper.selectById("doc-1")).thenReturn(document);
        MockMultipartFile file = new MockMultipartFile("file", "hack.exe", "application/octet-stream", "x".getBytes());

        assertThatThrownBy(() -> documentService.uploadNewVersion("doc-1", file, null, 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的文件类型");
        verify(documentFileStorageService, never()).saveFile(anyString(), anyString(), any(), anyString());
    }

    @Test
    void uploadNewVersionRejectsOversizedFile() {
        Document document = newDocument("doc-1", 7L);
        when(documentMapper.selectById("doc-1")).thenReturn(document);
        byte[] big = new byte[10485761];
        MockMultipartFile file = new MockMultipartFile("file", "big.md", "text/markdown", big);

        assertThatThrownBy(() -> documentService.uploadNewVersion("doc-1", file, null, 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件大小超过限制");
        verify(documentFileStorageService, never()).saveFile(anyString(), anyString(), any(), anyString());
    }

    @Test
    void listVersionsReturnsVosOrderedByVersionNumberDesc() {
        Document document = newDocument("doc-1", 7L);
        when(documentMapper.selectById("doc-1")).thenReturn(document);
        DocumentVersion v2 = versionRow("v2", "doc-1", 2, "h2");
        DocumentVersion v1 = versionRow("v1", "doc-1", 1, "h1");
        when(documentVersionMapper.selectByDocumentId("doc-1")).thenReturn(List.of(v2, v1));

        List<DocumentVersionVO> result = documentService.listVersions("doc-1", 7L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getVersionNumber()).isEqualTo(2);
        assertThat(result.get(1).getVersionNumber()).isEqualTo(1);
    }

    @Test
    void getVersionContentReadsRawFileFromMinio() {
        Document document = newDocument("doc-1", 7L);
        document.setBucketName("user-7");
        when(documentMapper.selectById("doc-1")).thenReturn(document);
        DocumentVersion version = versionRow("ver-1", "doc-1", 1, "hash-abc");
        version.setFileUrl("document-files/doc-1/v1/合同.md");
        when(documentVersionMapper.selectById("ver-1")).thenReturn(version);
        when(documentFileStorageService.readFile("user-7", "document-files/doc-1/v1/合同.md"))
                .thenReturn("hello".getBytes(StandardCharsets.UTF_8));

        byte[] content = documentService.getVersionContent("doc-1", "ver-1", 7L);

        assertThat(new String(content, StandardCharsets.UTF_8)).isEqualTo("hello");
    }

    @Test
    void getVersionContentFallsBackToVersionTextWhenNoRawFile() {
        Document document = newDocument("doc-1", 7L);
        when(documentMapper.selectById("doc-1")).thenReturn(document);
        DocumentVersion version = versionRow("ver-1", "doc-1", 1, "hash-abc");
        version.setContent("版本文本");
        when(documentVersionMapper.selectById("ver-1")).thenReturn(version);

        byte[] content = documentService.getVersionContent("doc-1", "ver-1", 7L);

        assertThat(new String(content, StandardCharsets.UTF_8)).isEqualTo("版本文本");
        verify(documentFileStorageService, never()).readFile(anyString(), anyString());
    }

    @Test
    void getVersionContentRejectsVersionFromOtherDocument() {
        Document document = newDocument("doc-1", 7L);
        when(documentMapper.selectById("doc-1")).thenReturn(document);
        DocumentVersion version = versionRow("ver-1", "doc-2", 1, "hash-abc");
        when(documentVersionMapper.selectById("ver-1")).thenReturn(version);

        assertThatThrownBy(() -> documentService.getVersionContent("doc-1", "ver-1", 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("版本不存在");
        verify(documentFileStorageService, never()).readFile(anyString(), anyString());
    }

    @Test
    void restoreVersionRestoresTextContentDirectly() {
        Document document = newDocument("doc-1", 7L);
        document.setBucketName("user-7");
        document.setVersion(3);
        when(documentMapper.selectById("doc-1")).thenReturn(document);
        DocumentVersion version = versionRow("ver-1", "doc-1", 1, "hash-abc");
        version.setContent("旧版本文本");
        when(documentVersionMapper.selectByDocumentIdAndVersion("doc-1", 1)).thenReturn(version);
        when(documentContentService.getContent("doc-1", "user-7")).thenReturn("当前内容");

        var vo = documentService.restoreVersion("doc-1", 1, 7L);

        verify(documentContentService).updateContent(eq("doc-1"), eq("user-7"), eq("旧版本文本"));
        verify(documentFileStorageService, never()).readFile(anyString(), anyString());
        assertThat(vo.getContent()).isEqualTo("旧版本文本");
        assertThat(document.getVersion()).isEqualTo(4);
    }

    @Test
    void restoreVersionReparsesRawFileWhenVersionHasNoText() {
        Document document = newDocument("doc-1", 7L);
        document.setBucketName("user-7");
        document.setVersion(3);
        when(documentMapper.selectById("doc-1")).thenReturn(document);
        DocumentVersion version = versionRow("ver-2", "doc-1", 2, "hash-abc");
        version.setFileUrl("document-files/doc-1/v2/合同.md");
        when(documentVersionMapper.selectByDocumentIdAndVersion("doc-1", 2)).thenReturn(version);
        when(documentContentService.getContent("doc-1", "user-7")).thenReturn("当前内容");
        when(documentFileStorageService.readFile("user-7", "document-files/doc-1/v2/合同.md"))
                .thenReturn("原始文件内容".getBytes(StandardCharsets.UTF_8));

        var vo = documentService.restoreVersion("doc-1", 2, 7L);

        verify(documentFileStorageService).readFile("user-7", "document-files/doc-1/v2/合同.md");
        verify(documentContentService).updateContent(eq("doc-1"), eq("user-7"), eq("原始文件内容"));
        assertThat(vo.getContent()).isEqualTo("原始文件内容");
    }

    @Test
    void restoreVersionRejectsMissingVersion() {
        Document document = newDocument("doc-1", 7L);
        when(documentMapper.selectById("doc-1")).thenReturn(document);
        when(documentVersionMapper.selectByDocumentIdAndVersion("doc-1", 99)).thenReturn(null);

        assertThatThrownBy(() -> documentService.restoreVersion("doc-1", 99, 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("版本不存在");
    }

    @Test
    void updateVersionNoteUpdatesNoteOnVersion() {
        Document document = newDocument("doc-1", 7L);
        DocumentVersion version = versionRow("ver-1", "doc-1", 1, "hash-abc");
        when(documentVersionMapper.selectById("ver-1")).thenReturn(version);
        when(documentMapper.selectById("doc-1")).thenReturn(document);

        documentService.updateVersionNote("ver-1", "新备注", 7L);

        assertThat(version.getNote()).isEqualTo("新备注");
        verify(documentVersionMapper).updateById(version);
    }

    private Document newDocument(String id, Long userId) {
        Document document = new Document();
        document.setId(id);
        document.setTitle("合同");
        document.setUserId(userId);
        return document;
    }

    private DocumentVersion versionRow(String id, String documentId, int versionNumber, String commitHash) {
        DocumentVersion version = new DocumentVersion();
        version.setId(id);
        version.setDocumentId(documentId);
        version.setVersionNumber(versionNumber);
        version.setTitle("合同");
        version.setCommitHash(commitHash);
        return version;
    }
}
