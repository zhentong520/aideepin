package com.moyz.adi.common.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.entity.DocumentSegment;
import com.moyz.adi.common.entity.IndexTask;
import com.moyz.adi.common.entity.KbDocument;
import com.moyz.adi.common.entity.KnowledgeBase;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.enums.SegmentModeEnum;
import com.moyz.adi.common.mapper.IndexTaskMapper;
import com.moyz.adi.common.mapper.KbDocumentMapper;
import com.moyz.adi.common.task.TaskQueueService;
import com.moyz.adi.common.task.TaskResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S2-02：IndexTaskService 迁入 task_queue 后的编排单元测试。
 * <p>
 * 覆盖两条新链路的分支语义（执行体本身由既有 IT/真实库覆盖）：
 * <ul>
 *   <li>enqueue → task_queue submit（Outbox 同事务）：payload 带 indexTaskId、优先级透传、
 *       upsert 被吞（同版本 running，enqueue 返回 null）时跳过提交；</li>
 *   <li>executeById：行不存在/已终态 → ok（不重试，保持「失败等手工重试」旧语义）；
 *       行 pending/running 但抢不到 → defer（retry 不前进）。</li>
 * </ul>
 */
class IndexTaskServiceQueueTest {

    private IndexTaskService indexTaskService;
    private IndexTaskMapper indexTaskMapper;
    private KbDocumentMapper kbDocumentMapper;
    private DocumentSegmentService documentSegmentService;
    private TaskQueueService taskQueueService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        indexTaskService = new IndexTaskService();
        indexTaskMapper = mock(IndexTaskMapper.class);
        kbDocumentMapper = mock(KbDocumentMapper.class);
        documentSegmentService = mock(DocumentSegmentService.class);
        taskQueueService = mock(TaskQueueService.class);

        ReflectionTestUtils.setField(indexTaskService, "indexTaskMapper", indexTaskMapper);
        ReflectionTestUtils.setField(indexTaskService, "kbDocumentMapper", kbDocumentMapper);
        ReflectionTestUtils.setField(indexTaskService, "documentSegmentService", documentSegmentService);
        ReflectionTestUtils.setField(indexTaskService, "taskQueueService", taskQueueService);
        ReflectionTestUtils.setField(indexTaskService, "objectMapper", objectMapper);
    }

    // ---------- enqueue → task_queue submit ----------

    @Test
    void enqueueSegmentSubmitsKbIndexTaskWithGivenPriority() {
        // enqueueSegment 无 chain update 副作用，是 submit 编排的最小验证面
        when(indexTaskMapper.enqueue(any(IndexTask.class))).thenReturn(555L);

        KnowledgeBase kb = new KnowledgeBase();
        kb.setUuid("kb-uuid");
        KbDocument doc = new KbDocument();
        doc.setUuid("doc-uuid");
        DocumentSegment segment = new DocumentSegment();
        segment.setUuid("seg-uuid");
        segment.setIndexVersion(2);
        User user = new User();
        user.setId(42L);

        indexTaskService.enqueueSegment(kb, doc, segment, "embedding", user, IndexTaskService.PRIORITY_SYNC);

        ArgumentCaptor<ObjectNode> payload = ArgumentCaptor.forClass(ObjectNode.class);
        verify(taskQueueService).submit(eq("KB_INDEX"), payload.capture(),
                eq(IndexTaskService.PRIORITY_SYNC), eq(42L));
        assertEquals(555L, payload.getValue().get("indexTaskId").asLong(), "payload 必须携带 index_task 行主键");
    }

    @Test
    void enqueueDocumentDefaultsToUserPriority() {
        // QA 模式空文档：无段落可索引，绕开 chain update，聚焦 submit 编排
        KbDocument doc = qaDoc();
        when(kbDocumentMapper.getByUuid("doc-uuid")).thenReturn(doc);
        when(documentSegmentService.listByDocUuid("doc-uuid")).thenReturn(List.of());
        when(indexTaskMapper.enqueue(any(IndexTask.class))).thenReturn(777L);
        User user = user();

        indexTaskService.enqueueDocument("kb-uuid", "doc-uuid", "embedding", user);

        verify(taskQueueService).submit(eq("KB_INDEX"), any(ObjectNode.class),
                eq(IndexTaskService.PRIORITY_USER), eq(42L));
    }

    @Test
    void enqueueSkipsSubmitWhenUpsertSwallowedByRunningTask() {
        // 同版本已有 running 任务：enqueue（upsert）返回 null，该版本自有执行者收尾，
        // 不再投 task_queue——否则会与执行中任务重复执行
        when(indexTaskMapper.enqueue(any(IndexTask.class))).thenReturn(null);

        KnowledgeBase kb = new KnowledgeBase();
        kb.setUuid("kb-uuid");
        KbDocument doc = new KbDocument();
        doc.setUuid("doc-uuid");
        DocumentSegment segment = new DocumentSegment();
        segment.setUuid("seg-uuid");
        User user = user();

        indexTaskService.enqueueSegment(kb, doc, segment, "embedding", user, IndexTaskService.PRIORITY_USER);

        verify(taskQueueService, never()).submit(anyString(), any(ObjectNode.class), anyInt(), anyLong());
    }

    // ---------- executeById 分支 ----------

    @Test
    void executeByIdReturnsOkWhenRowAlreadyTerminal() {
        when(indexTaskMapper.claimById(9L)).thenReturn(null);
        IndexTask row = new IndexTask();
        row.setStatus(IndexTaskService.STATUS_DONE);
        when(indexTaskMapper.selectById(9L)).thenReturn(row);

        TaskResult result = indexTaskService.executeById(9L);

        // done/failed → ok：不重试（被取消/被新版本取代/上次失败等手工重试语义）
        assertTrue(result.success());
    }

    @Test
    void executeByIdReturnsOkWhenRowFailed() {
        when(indexTaskMapper.claimById(9L)).thenReturn(null);
        IndexTask row = new IndexTask();
        row.setStatus(IndexTaskService.STATUS_FAILED);
        when(indexTaskMapper.selectById(9L)).thenReturn(row);

        assertTrue(indexTaskService.executeById(9L).success());
    }

    @Test
    void executeByIdReturnsOkWhenRowMissing() {
        when(indexTaskMapper.claimById(9L)).thenReturn(null);
        when(indexTaskMapper.selectById(9L)).thenReturn(null);

        TaskResult result = indexTaskService.executeById(9L);

        assertTrue(result.success(), "行不存在（事务回滚等）→ ok，无事可做");
    }

    @Test
    void executeByIdDefersWhenRowNotClaimable() {
        when(indexTaskMapper.claimById(9L)).thenReturn(null);
        IndexTask row = new IndexTask();
        row.setStatus("pending");
        row.setDocUuid("doc-uuid");
        when(indexTaskMapper.selectById(9L)).thenReturn(row);

        TaskResult result = indexTaskService.executeById(9L);

        // 行 pending 但抢不到（同文档串行门未开）→ defer，不是 fail
        assertFalse(result.success());
        assertTrue(result.isDeferred());
        assertEquals(60L, result.deferSeconds());
        assertTrue(result.error().contains("doc-uuid"));
    }

    // ---------- fixtures ----------

    private KbDocument qaDoc() {
        KbDocument doc = new KbDocument();
        doc.setId(1L);
        doc.setUuid("doc-uuid");
        doc.setIsDeleted(false);
        doc.setSegmentMode(SegmentModeEnum.QA);
        doc.setIndexVersion(3);
        return doc;
    }

    private User user() {
        User user = new User();
        user.setId(42L);
        return user;
    }
}
