package com.moyz.adi.common.multiagent;

import com.moyz.adi.common.multiagent.BlackboardEntry.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T7-03 共享黑板（{@link Blackboard}）单测——任务书点名的三条：
 * <ol>
 *   <li>多子任务并行写入不丢；</li>
 *   <li>汇总正确（全部终态后 summary 覆盖每一项）；</li>
 *   <li>某子任务失败时黑板上有失败记录且不阻塞其他。</li>
 * </ol>
 * 另覆盖生命周期（PENDING→RUNNING→终态）、taskId 去重参数、空板边界。
 */
class BlackboardTest {

    @Test
    @DisplayName("生命周期：分派→执行→成功回写，状态与内容可查")
    void lifecycleFromAssignToSuccess() {
        Blackboard board = new Blackboard();
        board.assign("t1", "分析销售数据");
        assertEquals(1, board.size());
        assertFalse(board.allDone());
        assertEquals(Status.PENDING, board.read("t1").orElseThrow().getStatus());

        board.markRunning("t1");
        assertEquals(Status.RUNNING, board.read("t1").orElseThrow().getStatus());

        board.writeResult("t1", "增长 12%");
        BlackboardEntry entry = board.read("t1").orElseThrow();
        assertEquals(Status.SUCCESS, entry.getStatus());
        assertEquals("增长 12%", entry.getResult());
        assertTrue(entry.isDone());
        assertTrue(board.allDone());
    }

    @Test
    @DisplayName("失败记录：writeFailure 落 FAIL 终态与错误信息，不抛不阻塞")
    void failureIsRecordedNotThrown() {
        Blackboard board = new Blackboard();
        board.assign("t1", "任务一");
        board.assign("t2", "任务二");
        board.writeFailure("t1", "llm timeout");
        board.writeResult("t2", "正常结果");

        assertEquals(1, board.failCount());
        assertEquals(1, board.successCount());
        assertEquals(Status.FAIL, board.read("t1").orElseThrow().getStatus());
        assertEquals("llm timeout", board.read("t1").orElseThrow().getErrorMessage());
        // 失败不阻塞其他：t2 正常终态
        assertEquals(Status.SUCCESS, board.read("t2").orElseThrow().getStatus());
        assertTrue(board.allDone());
    }

    @Test
    @DisplayName("多子任务并行写入不丢：64 个任务并发回写，板上一项不少、终态齐全")
    void parallelWritesDoNotLoseEntries() throws Exception {
        int taskCount = 64;
        Blackboard board = new Blackboard();
        for (int i = 0; i < taskCount; i++) {
            board.assign("task-" + i, "子任务 " + i);
        }

        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(taskCount);
        for (int i = 0; i < taskCount; i++) {
            final String taskId = "task-" + i;
            final int idx = i;
            pool.execute(() -> {
                try {
                    start.await(); // 统一闸口，最大化并发冲突
                    board.markRunning(taskId);
                    if (idx % 2 == 0) {
                        board.writeResult(taskId, "结果-" + taskId);
                    } else {
                        board.writeFailure(taskId, "错误-" + taskId);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "并发回写应在 10s 内完成");
        pool.shutdown();

        // 一项不丢：64 项全在板上，成功+失败=64
        assertEquals(taskCount, board.size());
        assertEquals(taskCount, board.successCount() + board.failCount());
        assertTrue(board.allDone());
        assertEquals(0, board.pendingCount());
        // 内容正确：每项都能按 taskId 读回自己的结果，无串写无覆盖
        for (int i = 0; i < taskCount; i++) {
            String taskId = "task-" + i;
            BlackboardEntry entry = board.read(taskId).orElseThrow();
            if (i % 2 == 0) {
                assertEquals(Status.SUCCESS, entry.getStatus());
                assertEquals("结果-" + taskId, entry.getResult());
            } else {
                assertEquals(Status.FAIL, entry.getStatus());
                assertEquals("错误-" + taskId, entry.getErrorMessage());
            }
        }
    }

    @Test
    @DisplayName("汇总正确：summary 覆盖全部终态，成功给结果、失败给原因")
    void summaryCoversEveryEntry() {
        Blackboard board = new Blackboard();
        board.assign("alpha", "任务A");
        board.assign("beta", "任务B");
        board.assign("gamma", "任务C");
        board.writeResult("alpha", "A 的结论");
        board.writeFailure("beta", "B 崩了");
        board.writeResult("gamma", "C 的结论");

        String summary = board.summary();
        assertTrue(summary.contains("共 3 项，成功 2 项，失败 1 项"), summary);
        assertTrue(summary.contains("[alpha] 成功：A 的结论"), summary);
        assertTrue(summary.contains("[beta] 失败：B 崩了"), summary);
        assertTrue(summary.contains("[gamma] 成功：C 的结论"), summary);

        // resultsOnly 只聚合成功结果，供 summary_prompt 的 {results} 占位
        String results = board.resultsOnly();
        assertTrue(results.contains("[alpha] A 的结论"), results);
        assertTrue(results.contains("[gamma] C 的结论"), results);
        assertFalse(results.contains("B 崩了"), "resultsOnly 只放成功结果，实际：" + results);
    }

    @Test
    @DisplayName("汇总稳定可 diff：allEntries 按 taskId 排序")
    void allEntriesSortedByTaskId() {
        Blackboard board = new Blackboard();
        board.assign("zeta", "z");
        board.assign("alpha", "a");
        board.assign("mid", "m");
        List<String> ids = new ArrayList<>();
        for (BlackboardEntry entry : board.allEntries()) {
            ids.add(entry.getTaskId());
        }
        assertEquals(List.of("alpha", "mid", "zeta"), ids);
    }

    @Test
    @DisplayName("边界：空白 taskId 拒参；空板 allDone=false、summary 为空")
    void edgesBlankTaskIdAndEmptyBoard() {
        Blackboard board = new Blackboard();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> board.assign("  ", "x"));
        assertFalse(board.allDone(), "空板不算全部完成");
        assertEquals("", board.summary());
        assertEquals("", board.resultsOnly());
    }

    @Test
    @DisplayName("未登记 taskId 的回写被忽略（不炸、不造鬼条目）")
    void writeToUnknownTaskIsIgnored() {
        Blackboard board = new Blackboard();
        board.writeResult("ghost", "无主结果");
        board.writeFailure("ghost", "无主失败");
        assertEquals(0, board.size());
    }
}
