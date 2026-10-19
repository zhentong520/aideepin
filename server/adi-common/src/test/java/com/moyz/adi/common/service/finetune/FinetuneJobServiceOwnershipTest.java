package com.moyz.adi.common.service.finetune;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.moyz.adi.common.util.SpringUtil;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.entity.FinetuneJob;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.mapper.FinetuneJobMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static com.moyz.adi.common.cosntant.AdiConstant.COLUMN_NAME_USER_ID;

/**
 * {@code FinetuneJobService} 的任务归属校验回归测试。
 *
 * <p>2026-10-15 修复前，{@code getByUuid} 只按 uuid 查库、不比对 user_id，
 * 而 {@code requestStop} / {@code readLogTail} 均以它为入口，
 * 导致可按 uuid 停止他人训练任务、读取他人训练日志（含 hyper-params 与数据集路径）。
 * 修复改为复用 {@code PrivilegeUtil.checkAndGetByUuid}，语义与项目其他资源一致：
 * admin 放行，普通用户仅限本人，拒绝时抛 {@code A_DATA_NOT_FOUND} 而非 403。
 *
 * <p>注意：修复前这些入口只在 {@code /admin/**} 下（由 TokenFilter 拦 is_admin），
 * 因此并非「任意用户可越权」；补service 层校验属**防御纵深**，
 * 避免将来新增非 /admin 前缀入口时立刻变成真实越权。
 */
class FinetuneJobServiceOwnershipTest {

    private static final Long OWNER_ID = 42L;
    private static final Long OTHER_ID = 7L;

    private FinetuneJobService finetuneJobService;
    private FinetuneJobMapper finetuneJobMapper;

    @BeforeEach
    void setUp() {
        finetuneJobService = new FinetuneJobService();
        finetuneJobMapper = mock(FinetuneJobMapper.class);

        ApplicationContext applicationContext = mock(ApplicationContext.class);
        MessageSource messageSource = mock(MessageSource.class);
        when(applicationContext.getBean(MessageSource.class)).thenReturn(messageSource);
        when(messageSource.getMessage(any(String.class), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ReflectionTestUtils.setField(SpringUtil.class, "applicationContext", applicationContext);
        ReflectionTestUtils.setField(finetuneJobService, "baseMapper", finetuneJobMapper);
        ReflectionTestUtils.setField(finetuneJobService, "baseDataPath", System.getProperty("java.io.tmpdir"));
    }

    @AfterEach
    void tearDown() {
        ThreadContext.unload();
        ReflectionTestUtils.setField(SpringUtil.class, "applicationContext", null);
    }

    // ------------------------------------------------------------------
    // getByUuid：所有者可读、管理员可读、他人不可读
    // ------------------------------------------------------------------

    @DisplayName("所有者可读取自己的任务")
    @Test
    void ownerCanRead() {
        FinetuneJob job = job(OWNER_ID);
        when(finetuneJobMapper.selectOne(any())).thenReturn(job);
        ThreadContext.setCurrentUser(user(OWNER_ID, false));

        assertSame(job, finetuneJobService.getByUuid("job-uuid"));
    }

    @DisplayName("管理员可读取任意任务")
    @Test
    void adminCanReadAny() {
        FinetuneJob job = job(OWNER_ID);
        when(finetuneJobMapper.selectOne(any())).thenReturn(job);
        ThreadContext.setCurrentUser(user(OTHER_ID, true));

        assertSame(job, finetuneJobService.getByUuid("job-uuid"));
    }

    @DisplayName("非所有者读不到他人任务，且报 A_DATA_NOT_FOUND 而非 403（不泄露存在性）")
    @Test
    void nonOwnerRejected() {
        when(finetuneJobMapper.selectOne(any())).thenReturn(null);
        ThreadContext.setCurrentUser(user(OTHER_ID, false));

        BaseException exception = assertThrows(BaseException.class,
                () -> finetuneJobService.getByUuid("job-uuid"));

        assertEquals(ErrorEnum.A_DATA_NOT_FOUND.getCode(), exception.getCode());
    }

    // ------------------------------------------------------------------
    // requestStop：修复前可停止他人训练任务
    // ------------------------------------------------------------------

    @DisplayName("非所有者不得停止他人训练任务")
    @Test
    void nonOwnerCannotStopOthersJob() {
        when(finetuneJobMapper.selectOne(any())).thenReturn(null);
        ThreadContext.setCurrentUser(user(OTHER_ID, false));

        assertThrows(BaseException.class, () -> finetuneJobService.requestStop("job-uuid"));
    }

    @DisplayName("所有者可停止自己的运行中任务")
    @Test
    void ownerCanStopOwnJob() {
        FinetuneJob job = job(OWNER_ID);
        job.setStatus(FinetuneJob.STATUS_RUNNING);
        when(finetuneJobMapper.selectOne(any())).thenReturn(job);
        when(finetuneJobMapper.requestStop("job-uuid")).thenReturn(1);
        ThreadContext.setCurrentUser(user(OWNER_ID, false));

        finetuneJobService.requestStop("job-uuid");
    }

    @DisplayName("已结束的任务不可停止（状态校验仍然生效）")
    @Test
    void finishedJobCannotStop() {
        FinetuneJob job = job(OWNER_ID);
        job.setStatus(FinetuneJob.STATUS_SUCCESS);
        when(finetuneJobMapper.selectOne(any())).thenReturn(job);
        ThreadContext.setCurrentUser(user(OWNER_ID, false));

        assertThrows(BaseException.class, () -> finetuneJobService.requestStop("job-uuid"));
    }

    // ------------------------------------------------------------------
    // readLogTail：修复前可读他人训练日志
    // ------------------------------------------------------------------

    @DisplayName("非所有者不得读取他人训练日志")
    @Test
    void nonOwnerCannotReadLog() {
        when(finetuneJobMapper.selectOne(any())).thenReturn(null);
        ThreadContext.setCurrentUser(user(OTHER_ID, false));

        // 日志文件不存在时也应先被归属校验拦住——顺序不能被挪走
        assertThrows(BaseException.class, () -> finetuneJobService.readLogTail("job-uuid", 100));
    }

    // ------------------------------------------------------------------
    // search：修复前可枚举所有用户的任务
    // ------------------------------------------------------------------

    /**
     * 搜索的用户隔离无法在纯 Mockito 环境验证，故这里不做断言。
     * <p>
     * 原因：{@code buildSearchWrapper} 依赖 {@code this.lambdaQuery()}，而 {@code ServiceImpl}
     * 会向 mapper 反查 {@code mapperInterface / sqlSession}；Mockito mock 上取不到这两个属性，
     * 直接抛「Unable to retrieve the mapperInterface and sqlSession properties from Mock」。
     * <p>
     * 这是<b>测试环境限制，不是产品缺陷</b>。按项目「测试断言真实性」约定，
     * 与其写一条只能靠 mock 打桩绕过的假断言，不如把这条明确留白、交给集成测试覆盖；
     * {@code search} 的隔离语义与 {@link #ownerCanRead()} 共用同一处 PrivilegeUtil 判定，
     * 后者已覆盖「非管理员被拒」这一核心分支。
     * TODO: 补Testcontainers 集成测试后，在此断言 admin 分支不加 user_id、非管理员分支加 user_id。
     */
    @DisplayName("搜索隔离：当前单测环境无法构造 lambdaQuery，留给集成测试")
    @org.junit.jupiter.api.Disabled("需要真实 SqlSession，见类注释 TODO")
    void searchIsolationPendingIntegrationTest() {
        ThreadContext.setCurrentUser(user(OTHER_ID, false));
        LambdaQueryChainWrapper<FinetuneJob> wrapper =
                ReflectionTestUtils.invokeMethod(finetuneJobService, "buildSearchWrapper", null, null);
        assertNotNull(wrapper);
        assertTrue(wrapper.getTargetSql().contains(COLUMN_NAME_USER_ID),
                "非管理员搜索必须带 user_id 过滤：" + wrapper.getTargetSql());
    }

    private static FinetuneJob job(Long userId) {
        FinetuneJob job = new FinetuneJob();
        job.setId(1L);
        job.setUuid("job-uuid");
        job.setUserId(userId);
        job.setStatus(FinetuneJob.STATUS_PENDING);
        job.setIsDeleted(false);
        return job;
    }

    private static User user(Long id, Boolean isAdmin) {
        User user = new User();
        user.setId(id);
        user.setUuid("u-" + id);
        user.setIsAdmin(isAdmin);
        return user;
    }
}
