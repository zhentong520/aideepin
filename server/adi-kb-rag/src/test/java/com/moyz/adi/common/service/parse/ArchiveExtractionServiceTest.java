package com.moyz.adi.common.service.parse;

import com.moyz.adi.common.config.AdiProperties;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 压缩包解包单测（批次 A）：覆盖安全四件套与格式矩阵。
 * 安全基线 = RAGFlow CVE-2026-24770 修复标准（Zip Slip 消毒 / bomb 双闸 /
 * 拒绝链接条目 / 中文名编码兜底）。
 */
class ArchiveExtractionServiceTest {

    private AdiProperties adiProperties;
    private ArchiveExtractionService service;

    @BeforeEach
    void setUp() {
        adiProperties = new AdiProperties();
        adiProperties.getParse().getArchive().setEnabled(true);
        service = new ArchiveExtractionService(adiProperties);
    }

    private static byte[] zip(Map<String, byte[]> entries) throws Exception {
        return zip(entries, StandardCharsets.UTF_8);
    }

    private static byte[] zip(Map<String, byte[]> entries, Charset charset) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos, charset)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    private static byte[] tar(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tos = new TarArchiveOutputStream(bos)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                TarArchiveEntry entry = new TarArchiveEntry(e.getKey());
                entry.setSize(e.getValue().length);
                tos.putArchiveEntry(entry);
                tos.write(e.getValue());
                tos.closeArchiveEntry();
            }
        }
        return bos.toByteArray();
    }

    // ==================== 正常解包 ====================

    @Test
    void zipExtractsEntriesWithInnerPath() throws Exception {
        // LinkedHashMap：Map.of 无序，断言首条目顺序必须有确定迭代序
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("docs/a.txt", "hello".getBytes(StandardCharsets.UTF_8));
        entries.put("docs/sub/b.md", "# md".getBytes(StandardCharsets.UTF_8));
        byte[] zipBytes = zip(entries);
        ArchiveExtractionService.ArchiveExtractResult r = service.extract("pack.zip", zipBytes, 1);
        assertTrue(r.failures().isEmpty(), () -> "unexpected failures: " + r.failures());
        assertEquals(2, r.entries().size());
        assertEquals("docs/a.txt", r.entries().get(0).path());
        assertEquals("hello", new String(r.entries().get(0).data(), StandardCharsets.UTF_8));
    }

    @Test
    void plainGzSingleFile() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream gos = new java.util.zip.GZIPOutputStream(bos)) {
            gos.write("report body".getBytes(StandardCharsets.UTF_8));
        }
        ArchiveExtractionService.ArchiveExtractResult r = service.extract("report.pdf.gz", bos.toByteArray(), 1);
        assertTrue(r.failures().isEmpty());
        assertEquals(1, r.entries().size());
        assertEquals("report.pdf", r.entries().get(0).path());
        assertEquals("report body", new String(r.entries().get(0).data(), StandardCharsets.UTF_8));
    }

    @Test
    void tarGzExpandsTarEntries() throws Exception {
        byte[] tarBytes = tar(Map.of("inner/x.txt", "x".getBytes(StandardCharsets.UTF_8)));
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream gos = new java.util.zip.GZIPOutputStream(bos)) {
            gos.write(tarBytes);
        }
        ArchiveExtractionService.ArchiveExtractResult r = service.extract("bundle.tar.gz", bos.toByteArray(), 1);
        assertTrue(r.failures().isEmpty());
        assertEquals(1, r.entries().size());
        assertEquals("inner/x.txt", r.entries().get(0).path());
    }

    // ==================== Zip Slip（CVE-2026-24770 同族） ====================

    @Test
    void zipSlipTraversalEntryIsDropped() throws Exception {
        byte[] zipBytes = zip(Map.of(
                "../evil.txt", "boom".getBytes(StandardCharsets.UTF_8),
                "ok.txt", "fine".getBytes(StandardCharsets.UTF_8)));
        ArchiveExtractionService.ArchiveExtractResult r = service.extract("pack.zip", zipBytes, 1);
        assertEquals(1, r.entries().size());
        assertEquals("ok.txt", r.entries().get(0).path());
    }

    @Test
    void zipAbsoluteAndDriveLetterEntriesAreDropped() throws Exception {
        byte[] zipBytes = zip(Map.of(
                "/etc/passwd", "x".getBytes(StandardCharsets.UTF_8),
                "C:/windows/win.ini", "x".getBytes(StandardCharsets.UTF_8),
                "back\\slash.txt", "x".getBytes(StandardCharsets.UTF_8)));
        ArchiveExtractionService.ArchiveExtractResult r = service.extract("pack.zip", zipBytes, 1);
        // 反斜杠归一化为 / 后是合法相对路径；绝对路径与盘符被拒
        assertEquals(1, r.entries().size());
        assertEquals("back/slash.txt", r.entries().get(0).path());
    }

    @Test
    void sanitizeRejectsTraversalAbsoluteDriveAndNul() {
        assertNull(ArchiveExtractionService.sanitizeEntryName("../a"));
        assertNull(ArchiveExtractionService.sanitizeEntryName("a/../../b"));
        assertNull(ArchiveExtractionService.sanitizeEntryName("/abs"));
        assertNull(ArchiveExtractionService.sanitizeEntryName("C:\\win"));
        assertNull(ArchiveExtractionService.sanitizeEntryName("a\u0000b"));
        assertNull(ArchiveExtractionService.sanitizeEntryName("  "));
        assertNull(ArchiveExtractionService.sanitizeEntryName("__MACOSX/pack/./a"));
        // 绝对路径一律拒绝（与 zipAbsoluteAndDriveLetterEntriesAreDropped 一致），不做前缀剥离
        assertNull(ArchiveExtractionService.sanitizeEntryName("/a/b.txt"));
        assertEquals("a/b.txt", ArchiveExtractionService.sanitizeEntryName("a\\b.txt"));
    }

    // ==================== Zip Bomb 双闸 ====================

    @Test
    void bombRatioGuardAborts() throws Exception {
        // 20MB 全零可压到 ~20KB：压缩比 1000 倍，超过默认 max-ratio=100 → 整包中止
        byte[] big = new byte[20 * 1024 * 1024];
        byte[] zipBytes = zip(Map.of("bomb.bin", big));
        assertTrue(zipBytes.length < 200_000, "test premise: zip must compress well");
        ArchiveExtractionService.ArchiveExtractResult r = service.extract("bomb.zip", zipBytes, 1);
        assertTrue(r.failures().stream().anyMatch(f -> f.contains("压缩比")), () -> "failures: " + r.failures());
        assertTrue(r.entries().isEmpty());
    }

    @Test
    void maxEntriesGuardAborts() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (int i = 0; i < 15; i++) {
            entries.put("f" + i + ".txt", new byte[]{(byte) i});
        }
        byte[] zipBytes = zip(entries);
        adiProperties.getParse().getArchive().setMaxEntries(5);
        ArchiveExtractionService.ArchiveExtractResult r = service.extract("many.zip", zipBytes, 1);
        assertTrue(r.failures().stream().anyMatch(f -> f.contains("条目数")), () -> "failures: " + r.failures());
        assertTrue(r.entries().size() <= 5);
    }

    // ==================== 嵌套压缩包 ====================

    @Test
    void nestedZipBeyondDepthIsReportedNotSilent() throws Exception {
        byte[] inner = zip(Map.of("deep/a.txt", "a".getBytes(StandardCharsets.UTF_8)));
        byte[] outer = zip(Map.of("top.txt", "t".getBytes(StandardCharsets.UTF_8), "inner.zip", inner));
        ArchiveExtractionService.ArchiveExtractResult r = service.extract("outer.zip", outer, 1);
        assertEquals(1, r.entries().size());
        assertEquals("top.txt", r.entries().get(0).path());
        assertTrue(r.failures().stream().anyMatch(f -> f.contains("深度限制")), () -> "failures: " + r.failures());
    }

    @Test
    void nestedZipWithinDepthIsExpanded() throws Exception {
        adiProperties.getParse().getArchive().setMaxDepth(2);
        byte[] inner = zip(Map.of("deep/a.txt", "a".getBytes(StandardCharsets.UTF_8)));
        byte[] outer = zip(Map.of("inner.zip", inner));
        ArchiveExtractionService.ArchiveExtractResult r = service.extract("outer.zip", outer, 1);
        assertTrue(r.failures().isEmpty(), () -> "failures: " + r.failures());
        assertEquals(1, r.entries().size());
        assertEquals("inner.zip/deep/a.txt", r.entries().get(0).path());
    }

    // ==================== 中文名 GBK 兜底 ====================

    @Test
    void gbkZipEntryNamesRecovered() throws Exception {
        // Windows 中文压缩包典型形态：GBK 编码条目名、无 EFS 标志。
        // UTF-8 首轮出现 U+FFFD → 服务应整包按 GBK 重读恢复原名。
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("第三章\u884c\u653f\u533a\u5212.txt", "cn".getBytes(StandardCharsets.UTF_8));
        byte[] zipBytes = zip(entries, Charset.forName("GBK"));
        ArchiveExtractionService.ArchiveExtractResult r = service.extract("pack.zip", zipBytes, 1);
        assertTrue(r.failures().isEmpty(), () -> "failures: " + r.failures());
        assertEquals(1, r.entries().size());
        assertEquals("第三章行政区划.txt", r.entries().get(0).path());
        assertFalse(r.entries().get(0).path().contains("\uFFFD"));
    }

    // ==================== 其它 ====================

    @Test
    void tarSymlinkEntrySkipped() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tos = new TarArchiveOutputStream(bos)) {
            TarArchiveEntry link = new TarArchiveEntry("evil-link", TarArchiveEntry.LF_SYMLINK);
            link.setLinkName("../../etc/passwd");
            tos.putArchiveEntry(link);
            tos.closeArchiveEntry();
            TarArchiveEntry file = new TarArchiveEntry("ok.txt");
            byte[] data = "ok".getBytes(StandardCharsets.UTF_8);
            file.setSize(data.length);
            tos.putArchiveEntry(file);
            tos.write(data);
            tos.closeArchiveEntry();
        }
        ArchiveExtractionService.ArchiveExtractResult r = service.extract("pack.tar", bos.toByteArray(), 1);
        assertEquals(1, r.entries().size());
        assertEquals("ok.txt", r.entries().get(0).path());
        assertTrue(r.failures().stream().anyMatch(f -> f.contains("链接条目")));
    }

    @Test
    void unsupportedExtReportedExplicitly() {
        // rar 未放行（junrar 容错差，方案明确二期）
        ArchiveExtractionService.ArchiveExtractResult r = service.extract("x.rar", new byte[]{1}, 1);
        assertTrue(r.entries().isEmpty());
        assertTrue(r.failures().stream().anyMatch(f -> f.contains("不支持")));
    }

    @Test
    void knownAndAllowedExtClassification() {
        assertTrue(service.isKnownArchiveExt("a.zip"));
        assertTrue(service.isKnownArchiveExt("a.rar"));
        assertTrue(service.isAllowedArchiveExt("a.7z"));
        assertFalse(service.isAllowedArchiveExt("a.rar"));
        assertFalse(service.isKnownArchiveExt("a.pdf"));
        assertNotNull(service.cfg().getAllowedExts());
    }

    @Test
    void emptyAndGarbageZipFailWithReason() {
        ArchiveExtractionService.ArchiveExtractResult empty = service.extract("e.zip", new byte[0], 1);
        assertTrue(empty.entries().isEmpty());
        assertFalse(empty.failures().isEmpty());

        ArchiveExtractionService.ArchiveExtractResult garbage =
                service.extract("g.zip", "not a zip at all".getBytes(StandardCharsets.UTF_8), 1);
        assertTrue(garbage.entries().isEmpty());
        assertTrue(garbage.failures().stream().anyMatch(f -> f.contains("解压失败")));
    }
}
