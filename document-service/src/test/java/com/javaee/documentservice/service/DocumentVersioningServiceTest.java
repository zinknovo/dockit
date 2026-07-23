package com.javaee.documentservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javaee.common.exception.BusinessException;
import com.javaee.documentservice.entity.Document;
import com.javaee.documentservice.entity.DocumentVersion;
import com.javaee.documentservice.mapper.DocumentMapper;
import com.javaee.documentservice.mapper.DocumentVersionMapper;
import com.javaee.documentservice.service.impl.DocumentServiceImpl;
import com.javaee.documentservice.versioning.VersionControlProperties;
import com.javaee.documentservice.versioning.VersionControlService;
import com.javaee.documentservice.vo.DocumentVersionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

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
 * mock 掉 VersionControlService / DocumentFileStorageService / mapper，覆盖上传、列版本、取内容、改备注主链路
 */
class DocumentVersioningServiceTest {

    private DocumentMapper documentMapper;
    private DocumentVersionMapper documentVersionMapper;
    private DocumentContentService documentContentService;
    private DocumentAccessService documentAccessService;
    private VersionControlService versionControlService;
    private DocumentFileStorageService documentFileStorageService;
    private VersionControlProperties versionControlProperties;
    private DocumentServiceImpl documentService;

    @BeforeEach
    void setUp() {
        documentMapper = org.mockito.Mockito.mock(DocumentMapper.class);
        documentVersionMapper = org.mockito.Mockito.mock(DocumentVersionMapper.class);
        documentContentService = org.mockito.Mockito.mock(DocumentContentService.class);
        documentAccessService = org.mockito.Mockito.mock(DocumentAccessService.class);
        versionControlService = org.mockito.Mockito.mock(VersionControlService.class);
        documentFileStorageService = org.mockito.Mockito.mock(DocumentFileStorageService.class);
        versionControlProperties = new VersionControlProperties();

        documentService = new DocumentServiceImpl();
        ReflectionTestUtils.setField(documentService, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(documentService, "documentVersionMapper", documentVersionMapper);
        ReflectionTestUtils.setField(documentService, "documentContentService", documentContentService);
        ReflectionTestUtils.setField(documentService, "documentAccessService", documentAccessService);
        ReflectionTestUtils.setField(documentService, "versionControlService", versionControlService);
        ReflectionTestUtils.setField(documentService, "documentFileStorageService", documentFileStorageService);
        ReflectionTestUtils.setField(documentService, "versionControlProperties", versionControlProperties);
        ReflectionTestUtils.setField(documentService, "objectMapper", new ObjectMapper());
    }

    @Test
    void uploadNewVersionCommitsToGitStoresFileAndRecordsVersion() {
        Document document = newDocument("doc-1", 7L);
        document.setVersion(0);
        when(documentMapper.selectById("doc-1")).thenReturn(document);
        when(documentContentService.getBucketName(7L)).thenReturn("user-7");
        when(documentVersionMapper.selectMaxVersionNumber("doc-1")).thenReturn(0);
        when(versionControlService.repoExists("doc-1")).thenReturn(false);
        when(versionControlService.commitVersion(any())).thenReturn("commit-hash-123");
        when(documentFileStorageService.saveFile(eq("user-7"), anyString(), any(), anyString()))
                .thenReturn("document-files/doc-1/v1/合同.md");
        MockMultipartFile file = new MockMultipartFile("file", "合同.md", "text/markdown", "hello".getBytes());

        DocumentVersionVO vo = documentService.uploadNewVersion("doc-1", file, "初版", 7L);

        assertThat(vo.getVersionNumber()).isEqualTo(1);
        assertThat(vo.getCommitHash()).isEqualTo("commit-hash-123");
        assertThat(vo.getNote()).isEqualTo("初版");
        assertThat(vo.getFilePath()).isEqualTo("doc-1/合同.md");

        verify(versionControlService).initRepo("doc-1");
        ArgumentCaptor<VersionControlService.CommitRequest> commitCaptor =
                ArgumentCaptor.forClass(VersionControlService.CommitRequest.class);
        verify(versionControlService).commitVersion(commitCaptor.capture());
        assertThat(commitCaptor.getValue().scopeId()).isEqualTo("doc-1");
        assertThat(new String(commitCaptor.getValue().content())).isEqualTo("hello");

        ArgumentCaptor<DocumentVersion> versionCaptor = ArgumentCaptor.forClass(DocumentVersion.class);
        verify(documentVersionMapper).insert(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getCommitHash()).isEqualTo("commit-hash-123");
        assertThat(versionCaptor.getValue().getVersionNumber()).isEqualTo(1);
        assertThat(versionCaptor.getValue().getUploadedBy()).isEqualTo("7");

        ArgumentCaptor<Document> docCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentMapper).updateById(docCaptor.capture());
        assertThat(docCaptor.getValue().getVersion()).isEqualTo(1);
        assertThat(docCaptor.getValue().getFilePath()).isEqualTo("doc-1/合同.md");
        assertThat(docCaptor.getValue().getScopeId()).isEqualTo("doc-1");
    }

    @Test
    void uploadNewVersionRejectsUnsupportedExtension() {
        Document document = newDocument("doc-1", 7L);
        when(documentMapper.selectById("doc-1")).thenReturn(document);
        MockMultipartFile file = new MockMultipartFile("file", "hack.exe", "application/octet-stream", "x".getBytes());

        assertThatThrownBy(() -> documentService.uploadNewVersion("doc-1", file, null, 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的文件类型");
        verify(versionControlService, never()).commitVersion(any());
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
        assertThat(result.get(0).getCommitHash()).isEqualTo("h2");
        assertThat(result.get(1).getVersionNumber()).isEqualTo(1);
    }

    @Test
    void getVersionContentReadsFromGitAtCommitHash() {
        Document document = newDocument("doc-1", 7L);
        document.setScopeId("doc-1");
        when(documentMapper.selectById("doc-1")).thenReturn(document);
        DocumentVersion version = versionRow("ver-1", "doc-1", 1, "hash-abc");
        version.setFilePath("doc-1/合同.md");
        when(documentVersionMapper.selectById("ver-1")).thenReturn(version);
        when(versionControlService.readFileAtVersion("doc-1", "hash-abc", "doc-1/合同.md")).thenReturn("hello");

        String content = documentService.getVersionContent("doc-1", "ver-1", 7L);

        assertThat(content).isEqualTo("hello");
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
        verify(versionControlService, never()).readFileAtVersion(anyString(), anyString(), anyString());
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
