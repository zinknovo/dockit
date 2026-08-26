package com.javaee.documentservice.controller;

import com.javaee.common.constant.ErrorCodeEnum;
import com.javaee.common.model.Result;
import com.javaee.documentservice.security.RequestUserContext;
import com.javaee.documentservice.service.DocumentService;
import com.javaee.documentservice.vo.DocumentVersionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DocumentController 版本控制相关端点单元测试
 * 直接调用控制器方法，校验参数透传与 Result 包装（匹配项目纯单元测试风格）
 */
class DocumentVersionControllerTest {

    private DocumentService documentService;
    private RequestUserContext requestUserContext;
    private DocumentController controller;

    @BeforeEach
    void setUp() {
        documentService = Mockito.mock(DocumentService.class);
        requestUserContext = Mockito.mock(RequestUserContext.class);
        controller = new DocumentController(null, null);
        ReflectionTestUtils.setField(controller, "documentService", documentService);
        ReflectionTestUtils.setField(controller, "requestUserContext", requestUserContext);
        when(requestUserContext.getRequiredUserId()).thenReturn(7L);
    }

    @Test
    void getVersionsReturnsVoList() {
        when(documentService.listVersions("doc-1", 7L))
                .thenReturn(List.of(versionVo("ver-1", 1), versionVo("ver-2", 2)));

        Result<List<DocumentVersionVO>> result = controller.getVersions("doc-1");

        assertThat(result.getCode()).isEqualTo(ErrorCodeEnum.SUCCESS.getCode());
        assertThat(result.getData()).hasSize(2);
        verify(documentService).listVersions("doc-1", 7L);
    }

    @Test
    void uploadNewVersionDelegatesFileAndNoteToService() {
        DocumentVersionVO vo = versionVo("ver-2", 2);
        MockMultipartFile file = new MockMultipartFile("file", "a.md", "text/markdown", "hello".getBytes());
        when(documentService.uploadNewVersion(eq("doc-1"), any(MultipartFile.class), eq("初版"), eq(7L))).thenReturn(vo);

        Result<DocumentVersionVO> result = controller.uploadNewVersion("doc-1", file, "初版");

        assertThat(result.getCode()).isEqualTo(ErrorCodeEnum.SUCCESS.getCode());
        assertThat(result.getData()).isSameAs(vo);
        verify(documentService).uploadNewVersion(eq("doc-1"), any(MultipartFile.class), eq("初版"), eq(7L));
    }

    @Test
    void getVersionDetailDelegatesIdsToService() {
        DocumentVersionVO vo = versionVo("ver-1", 1);
        when(documentService.getVersionDetail("doc-1", "ver-1", 7L)).thenReturn(vo);

        Result<DocumentVersionVO> result = controller.getVersionDetail("doc-1", "ver-1");

        assertThat(result.getData()).isSameAs(vo);
        verify(documentService).getVersionDetail("doc-1", "ver-1", 7L);
    }

    @Test
    void getVersionContentDelegatesAndReturnsBytes() {
        byte[] bytes = "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(documentService.getVersionContent("doc-1", "ver-1", 7L)).thenReturn(bytes);

        byte[] result = controller.getVersionContent("doc-1", "ver-1");

        assertThat(result).isEqualTo(bytes);
        verify(documentService).getVersionContent("doc-1", "ver-1", 7L);
    }

    @Test
    void updateVersionNoteDelegatesToService() {
        Result<Void> result = controller.updateVersionNote("doc-1", "ver-1", "新备注");

        assertThat(result.getCode()).isEqualTo(ErrorCodeEnum.SUCCESS.getCode());
        verify(documentService).updateVersionNote("ver-1", "新备注", 7L);
    }

    private DocumentVersionVO versionVo(String id, int versionNumber) {
        DocumentVersionVO vo = new DocumentVersionVO();
        vo.setId(id);
        vo.setVersionNumber(versionNumber);
        return vo;
    }
}
