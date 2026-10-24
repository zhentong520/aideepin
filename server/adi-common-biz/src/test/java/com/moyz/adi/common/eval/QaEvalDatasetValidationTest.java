package com.moyz.adi.common.eval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 题库加载校验：题库有洞时宁可跑不起来，也不让分数悄悄失真。
 */
class QaEvalDatasetValidationTest {

    @TempDir
    Path tempDir;

    private Path write(String json) throws Exception {
        Path file = tempDir.resolve("dataset.json");
        Files.writeString(file, json);
        return file;
    }

    private static String item(String id, String type, String question, boolean refusal) {
        if (refusal) {
            return "{\"id\":\"%s\",\"type\":\"%s\",\"question\":\"%s\",\"refusalExpected\":true}".formatted(id, type, question);
        }
        return "{\"id\":\"%s\",\"type\":\"%s\",\"question\":\"%s\",\"expected\":[\"片段甲\"],\"sourceDocs\":[\"docA.md\"]}".formatted(id, type, question);
    }

    @Test
    void validDatasetLoads() throws Exception {
        StringBuilder sb = new StringBuilder("{\"meta\":{},\"items\":[");
        for (int i = 0; i < 30; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(item("q" + i, "fact", "问题" + i, false));
        }
        sb.append("]}");
        List<QaEvalItem> items = QaEvalRunner.loadDataset(write(sb.toString()));
        assertEquals(30, items.size());
        assertTrue(items.stream().allMatch(i -> !i.getExpected().isEmpty()));
    }

    @Test
    void datasetBelowThirtyIsRejected() throws Exception {
        StringBuilder sb = new StringBuilder("{\"meta\":{},\"items\":[");
        for (int i = 0; i < 29; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(item("q" + i, "fact", "问题" + i, false));
        }
        sb.append("]}");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> QaEvalRunner.loadDataset(write(sb.toString())));
        assertTrue(e.getMessage().contains("30"));
    }

    @Test
    void duplicateIdsAreRejected() throws Exception {
        StringBuilder sb = new StringBuilder("{\"meta\":{},\"items\":[");
        for (int i = 0; i < 30; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(item("same-id", "fact", "问题" + i, false));
        }
        sb.append("]}");
        assertThrows(IllegalArgumentException.class, () -> QaEvalRunner.loadDataset(write(sb.toString())));
    }

    @Test
    void factItemWithoutGoldIsRejected() throws Exception {
        StringBuilder sb = new StringBuilder("{\"meta\":{},\"items\":[");
        for (int i = 0; i < 29; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(item("q" + i, "fact", "问题" + i, false));
        }
        sb.append(",{\"id\":\"bad\",\"type\":\"fact\",\"question\":\"无金标\"}]}");
        assertThrows(IllegalArgumentException.class, () -> QaEvalRunner.loadDataset(write(sb.toString())));
    }

    @Test
    void refusalItemWithGoldIsRejected() throws Exception {
        StringBuilder sb = new StringBuilder("{\"meta\":{},\"items\":[");
        for (int i = 0; i < 30; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(item("q" + i, "fact", "问题" + i, false));
        }
        sb.append(",{\"id\":\"bad\",\"type\":\"refusal\",\"question\":\"拒答\",\"expected\":[\"不该有\"],\"refusalExpected\":true}]}");
        assertThrows(IllegalArgumentException.class, () -> QaEvalRunner.loadDataset(write(sb.toString())));
    }

    @Test
    void sourceDocsLengthMismatchIsRejected() throws Exception {
        StringBuilder sb = new StringBuilder("{\"meta\":{},\"items\":[");
        for (int i = 0; i < 30; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(item("q" + i, "fact", "问题" + i, false));
        }
        sb.append(",{\"id\":\"bad\",\"type\":\"multi_hop\",\"question\":\"错位\",\"expected\":[\"a\",\"b\"],\"sourceDocs\":[\"only-one\"]}]}");
        assertThrows(IllegalArgumentException.class, () -> QaEvalRunner.loadDataset(write(sb.toString())));
    }
}
