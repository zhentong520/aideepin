package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.toolkit.ChainWrappers;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.entity.AdiFile;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.file.FileOperatorContext;
import com.moyz.adi.common.mapper.FileMapper;
import com.moyz.adi.common.mapper.KbDocumentMapper;
import com.moyz.adi.common.util.SpringUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.MockedStatic;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S1-15 / G-10：文件物理删除前的引用检查 + 引用计数减一。
 * <p>
 * MP 3.5.14 的 {@code ServiceImpl.lambdaQuery()} 会从 baseMapper 反解 MyBatis 代理元数据，
 * Mockito mock 会直接抛 MybatisPlusException——因此用 spy + doReturn 注入
 * {@link ChainWrappers} 构造的链式 wrapper（与生产同一条执行路径，仅绕开代理元数据解析）。
 */
class FileServiceReferenceCheckTest {

    private FileService fileService;
    private FileMapper fileMapper;
    private KbDocumentMapper kbDocumentMapper;

    @BeforeEach
    void setUp() {
        fileMapper = mock(FileMapper.class);
        kbDocumentMapper = mock(KbDocumentMapper.class);
        fileService = spy(new FileService());

        ApplicationContext applicationContext = mock(ApplicationContext.class);
        MessageSource messageSource = mock(MessageSource.class);
        when(applicationContext.getBean(MessageSource.class)).thenReturn(messageSource);
        // BaseException 用 MessageFormat 把 {0} 替换为补充信息，返回纯模板保证 info 可断言
        when(messageSource.getMessage(any(String.class), any(), any())).thenAnswer(invocation -> "{0}");
        ReflectionTestUtils.setField(SpringUtil.class, "applicationContext", applicationContext);

        ReflectionTestUtils.setField(fileService, "baseMapper", fileMapper);
        ReflectionTestUtils.setField(fileService, "kbDocumentMapper", kbDocumentMapper);
        // 派生副本目录留空 → deleteDerivedCopies 全部跳过，单测不触碰真实文件系统
        ReflectionTestUtils.setField(fileService, "thumbnailsPath", "");
        ReflectionTestUtils.setField(fileService, "watermarkImagesPath", "");
        ReflectionTestUtils.setField(fileService, "watermarkThumbnailsPath", "");

        // set(SFunction, val) 即时解析 lambda 列，需要初始化 AdiFile 的 TableInfo 列缓存
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AdiFile.class);

        // ServiceImpl.lambdaQuery()/lambdaUpdate() 依赖 MyBatis 代理元数据，spy 注入链式 wrapper；
        // 每次 Answer 新建链，避免多测试共享同一 wrapper 造成 WHERE 条件污染
        doAnswer(inv -> ChainWrappers.lambdaQueryChain(fileMapper)).when(fileService).lambdaQuery();
        doAnswer(inv -> ChainWrappers.lambdaUpdateChain(fileMapper)).when(fileService).lambdaUpdate();

        ThreadContext.setCurrentUser(user());
    }

    @AfterEach
    void tearDown() {
        ThreadContext.unload();
        ReflectionTestUtils.setField(SpringUtil.class, "applicationContext", null);
    }

    @Test
    void removeReturnsFalseWhenFileNotFound() {
        when(fileMapper.selectOne(any())).thenReturn(null);

        assertFalse(fileService.removeFileAndSoftDel("no-such-file"));

        verify(kbDocumentMapper, never()).exists(any());
    }

    @Test
    void removeRejectsWhenReferencedByActiveKbDocument() {
        when(fileMapper.selectOne(any())).thenReturn(adiFile(null));
        when(kbDocumentMapper.exists(any())).thenReturn(true);

        BaseException exception = assertThrows(BaseException.class,
                () -> fileService.removeFileAndSoftDel("file-uuid"));

        assertEquals(ErrorEnum.A_FILE_IN_USE.getCode(), exception.getCode());
        // 被引用即拒绝：不软删记录
        verify(fileMapper, never()).update(any(), any());
    }

    @Test
    void removeRejectsWhenRefCountPositive() {
        when(fileMapper.selectOne(any())).thenReturn(adiFile(2));
        when(kbDocumentMapper.exists(any())).thenReturn(false);

        BaseException exception = assertThrows(BaseException.class,
                () -> fileService.removeFileAndSoftDel("file-uuid"));

        assertEquals(ErrorEnum.A_FILE_IN_USE.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("ref_count=2"));
        verify(fileMapper, never()).update(any(), any());
    }

    @Test
    void removeDeletesFileAndSoftDelRecordWhenNoReference() {
        AdiFile adiFile = adiFile(null);
        when(fileMapper.selectOne(any())).thenReturn(adiFile);
        when(kbDocumentMapper.exists(any())).thenReturn(false);
        when(fileMapper.update(any(), any())).thenReturn(1);

        try (MockedStatic<FileOperatorContext> operatorMock = mockStatic(FileOperatorContext.class)) {
            assertTrue(fileService.removeFileAndSoftDel("file-uuid"));

            // 主文件经 FileOperatorContext 物理删除（派生副本目录为空已跳过）
            operatorMock.verify(() -> FileOperatorContext.delete(adiFile));
        }
        // 记录软删落库
        verify(fileMapper).update(any(), any());
    }

    @Test
    void decrementRefCountReturnsTrueOnlyWhenRowAffected() {
        when(fileMapper.update(any(), any())).thenReturn(1).thenReturn(0);

        assertTrue(fileService.decrementRefCount("file-uuid"));
        // ref_count 已为 0 时 WHERE ref_count > 0 不命中 → 返回 false，不会减成负数
        assertFalse(fileService.decrementRefCount("file-uuid"));
    }

    private AdiFile adiFile(Integer refCount) {
        AdiFile adiFile = new AdiFile();
        adiFile.setId(5L);
        adiFile.setUuid("file-uuid");
        adiFile.setExt("png");
        adiFile.setRefCount(refCount);
        return adiFile;
    }

    private User user() {
        User user = new User();
        user.setId(42L);
        user.setIsAdmin(false);
        return user;
    }
}
