package com.moyz.adi.common.service.parse;

import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.config.props.Parse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 压缩包解包服务（二开批次 A，2026-11-05，见《知识库解析-深挖提升与压缩包处理方案.md》§2）。
 * <p>
 * Archive extraction for KB document upload: unpack zip/tar/gz/bz2/7z in memory and hand each
 * entry back so the caller can feed it through the regular upload chain.
 * <p>
 * <b>安全四件套</b>（对齐 RAGFlow CVE-2026-24770（CVSS 9.8，2026-01）修复标准——其 MinerU 解析器
 * 的 zip 解压无路径消毒导致 Zip Slip RCE）：
 * <ol>
 *   <li><b>Zip Slip</b>：条目名消毒——反斜杠归一化后拒绝 {@code ..}、绝对路径、盘符、NUL，
 *       条目名只用于标题展示，永不参与磁盘路径拼接；</li>
 *   <li><b>Zip Bomb</b>：单条目解压后大小 / 包体积 > max-ratio 即中止，累计解压字节
 *       > max-total-uncompressed-mb 即中止；</li>
 *   <li><b>拒绝加密条目与符号链接</b>：跳过并记失败原因，绝不静默；</li>
 *   <li><b>中文文件名</b>：zip 先按 UTF-8 解析，发现 U+FFFD 替换符（Windows GBK 包典型症状）
 *       则整个包按 GBK 重读。</li>
 * </ol>
 * 嵌套压缩包：深度 &le; max-depth（默认 1 = 只开最外层）才递归展开，超深记为失败原因。
 * 开关：{@code adi.parse.archive.enabled}，默认 false，关闭时上传入口直接拒绝（可回滚铁律 10）。
 */
@Slf4j
@Service
public class ArchiveExtractionService {

    /** 解压后的单文件条目；path 为包内相对路径（用于文档标题溯源与去重） */
    public record ExtractedEntry(String path, byte[] data) {
    }

    /** 解压结果：entries 可为空列表；failures 逐条给出「条目名 - 原因」，绝不静默丢弃 */
    public record ArchiveExtractResult(List<ExtractedEntry> entries, List<String> failures) {
    }

    /** 历史黑名单（FileService.BINARY_EXTENSIONS）里的压缩包后缀全集，用于「未开启/不支持」的明确报错 */
    private static final List<String> KNOWN_ARCHIVE_EXTS = List.of("zip", "rar", "7z", "tar", "gz", "bz2");

    /** 单文档缓存的图片总量护栏外的压缩包输入上限直接复用解压上限（输入不应大于解压输出上限） */
    private final AdiProperties adiProperties;

    public ArchiveExtractionService(AdiProperties adiProperties) {
        this.adiProperties = adiProperties;
    }

    public boolean isEnabled() {
        return cfg().isEnabled();
    }

    public Parse.Archive cfg() {
        return adiProperties.getParse().getArchive();
    }

    /** 扩展名是否属于「系统认识的压缩包」（含未放行的 rar 等，用于给明确错误而不是笼统的类型不支持） */
    public boolean isKnownArchiveExt(String fileName) {
        return KNOWN_ARCHIVE_EXTS.contains(extOf(fileName));
    }

    /** 扩展名是否在 adi.parse.archive.allowed-exts 放行名单内 */
    public boolean isAllowedArchiveExt(String fileName) {
        return allowedExts().contains(extOf(fileName));
    }

    private List<String> allowedExts() {
        return Arrays.stream(cfg().getAllowedExts().split(","))
                .map(String::trim).filter(StringUtils::isNotBlank).map(String::toLowerCase).toList();
    }

    private static String extOf(String fileName) {
        String name = StringUtils.defaultString(fileName);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 解压压缩包。任何安全护栏触发都中止并返回已解出的条目 + 失败原因清单，
     * 由调用方决定整体报错还是部分成功（本服务永不抛出除 IOException 包装外的异常）。
     *
     * @param fileName 压缩包文件名（决定解包器类型与单文件流的内层命名）
     * @param bytes    压缩包完整字节
     * @param depth    当前深度：最外层传 1；嵌套包 depth+1 &le; max-depth 才继续展开
     */
    public ArchiveExtractResult extract(String fileName, byte[] bytes, int depth) {
        ExtractState state = new ExtractState(bytes.length);
        String ext = extOf(fileName);
        try {
            switch (ext) {
                case "zip" -> extractZip(bytes, depth, state);
                case "tar" -> extractTar(new ByteArrayInputStream(bytes), fileName, depth, state);
                case "gz" -> extractSingleCompressed(bytes, fileName, depth, state, true);
                case "bz2" -> extractSingleCompressed(bytes, fileName, depth, state, false);
                case "7z" -> extract7z(bytes, depth, state);
                default -> state.failures.add(fileName + " - 不支持的压缩包格式: " + ext);
            }
        } catch (GuardException e) {
            log.warn("Archive extraction aborted, file:{}, reason:{}", fileName, e.getMessage());
            state.failures.add("(整包中止) " + e.getMessage());
        } catch (Exception e) {
            log.warn("Archive extraction failed, file:{}", fileName, e);
            state.failures.add(fileName + " - 解压失败: " + rootMessage(e));
        }
        // 绝不静默：解析「成功」却零条目零失败，多半是空包/损坏，必须给出明确原因
        if (state.entries.isEmpty() && state.failures.isEmpty()) {
            state.failures.add(fileName + " - 未解出任何文件条目（空包、损坏或仅含被跳过的元数据）");
        }
        return new ArchiveExtractResult(state.entries, state.failures);
    }

    // ==================== zip ====================

    private void extractZip(byte[] bytes, int depth, ExtractState state) throws IOException {
        // ZipArchiveInputStream 对非 zip 数据不抛异常而是静默返回 0 条目——先用 PK 魔数拦下损坏/伪装数据
        if (!looksLikeZip(bytes)) {
            throw new IOException("非 zip 格式数据（缺少 PK 头）");
        }
        // 中文名兜底第一道：按条目原始字节严格校验 UTF-8——GBK 包预判不依赖解码器替换符的具体形态
        //（不同 commons-compress 版本对不可解码字节可能给出 U+FFFD 或 '?'，后者曾使兜底漏判）
        Charset charset = hasNonUtf8RawNames(bytes) ? gbkCharset() : StandardCharsets.UTF_8;
        ArchiveExtractResult pass = readZip(bytes, depth, state, charset);
        // 第二道安全网：rawName 不可得（个别打包器）时退回 U+FFFD 探测
        boolean garbled = pass.entries().stream().anyMatch(e -> e.path().indexOf('\uFFFD') >= 0)
                || pass.failures().stream().anyMatch(f -> f.indexOf('\uFFFD') >= 0);
        if (garbled && StandardCharsets.UTF_8 == charset) {
            log.info("Zip entry names decoded with replacement chars, retrying as GBK");
            ExtractState gbkState = new ExtractState(bytes.length);
            readZip(bytes, depth, gbkState, gbkCharset());
            state.resetFrom(gbkState);
        }
    }

    /** zip 数据指纹：PK\x03\x04（本地头）/ PK\x05\x06（空包 EOCD）/ PK\x07\x08（跨卷） */
    private static boolean looksLikeZip(byte[] bytes) {
        return bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K'
                && (bytes[2] == 3 || bytes[2] == 5 || bytes[2] == 7);
    }

    /** 是否存在按 UTF-8 严格解码失败的条目名（Windows GBK 包的可靠指纹） */
    private boolean hasNonUtf8RawNames(byte[] bytes) throws IOException {
        try (ZipArchiveInputStream zin = new ZipArchiveInputStream(
                new ByteArrayInputStream(bytes), StandardCharsets.UTF_8.name(), true, true)) {
            ZipArchiveEntry entry;
            while (null != (entry = zin.getNextZipEntry())) {
                byte[] raw = entry.getRawName();
                if (null != raw && !isStrictUtf8(raw)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isStrictUtf8(byte[] bytes) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes));
            return true;
        } catch (java.nio.charset.CharacterCodingException e) {
            return false;
        }
    }

    private ArchiveExtractResult readZip(byte[] bytes, int depth, ExtractState state, java.nio.charset.Charset charset) throws IOException {
        try (ZipArchiveInputStream zin = new ZipArchiveInputStream(
                new ByteArrayInputStream(bytes), charset.name(), true, true)) {
            ZipArchiveEntry entry;
            while (null != (entry = zin.getNextZipEntry())) {
                if (entry.isDirectory()) {
                    continue;
                }
                String path = sanitizeEntryName(entry.getName());
                if (null == path) {
                    continue;
                }
                // 拒绝加密/不支持的压缩方法（canReadEntryData 对加密条目返回 false）
                if (!zin.canReadEntryData(entry)) {
                    state.failures.add(path + " - 条目加密或压缩方法不受支持，已跳过");
                    continue;
                }
                long size = entry.getSize();
                readGuarded(zin, size, path, state);
                accept(path, state.lastData, depth, state);
            }
        }
        return new ArchiveExtractResult(state.entries, state.failures);
    }

    // ==================== tar ====================

    private void extractTar(InputStream in, String displayName, int depth, ExtractState state) throws IOException {
        try (TarArchiveInputStream tin = new TarArchiveInputStream(in)) {
            TarArchiveEntry entry;
            while (null != (entry = tin.getNextTarEntry())) {
                if (entry.isDirectory()) {
                    continue;
                }
                // 拒绝符号链接/硬链接：目标路径不受本服务的消毒约束（Zip Slip 同族风险）
                if (entry.isSymbolicLink() || entry.isLink()) {
                    state.failures.add(entry.getName() + " - 链接条目已跳过");
                    continue;
                }
                String path = sanitizeEntryName(entry.getName());
                if (null == path) {
                    continue;
                }
                readGuarded(tin, entry.getSize(), path, state);
                accept(path, state.lastData, depth, state);
            }
        } catch (GuardException e) {
            throw e;
        } catch (IOException e) {
            if (state.entries.isEmpty()) {
                throw new IOException("tar 解析失败: " + rootMessage(e), e);
            }
            log.warn("tar parse ended with error after {} entries, display:{}", state.entries.size(), displayName, e);
        }
    }

    // ==================== gz / bz2 ====================

    private void extractSingleCompressed(byte[] bytes, String fileName, int depth, ExtractState state,
                                         boolean gzip) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (InputStream raw = new ByteArrayInputStream(bytes);
             InputStream comp = gzip ? new GzipCompressorInputStream(raw) : new BZip2CompressorInputStream(raw)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = comp.read(buf)) > 0) {
                bos.write(buf, 0, n);
                state.totalBytes += n;
                if (state.totalBytes > state.maxTotalBytes) {
                    throw new GuardException("解压总量超过上限 " + (state.maxTotalBytes / 1024 / 1024) + "MB");
                }
            }
        }
        byte[] inner = bos.toByteArray();
        String innerName = stripCompressSuffix(fileName);
        // .tar.gz / .tar.bz2：解出来的是 tar，继续按 tar 解；纯单文件（report.pdf.gz）即单条目
        if (innerName.toLowerCase(Locale.ROOT).endsWith(".tar") || looksLikeTar(inner)) {
            extractTar(new ByteArrayInputStream(inner), innerName, depth, state);
            return;
        }
        String path = sanitizeEntryName(innerName);
        if (null == path) {
            state.failures.add(fileName + " - 内层文件名不合法");
            return;
        }
        accept(path, inner, depth, state);
    }

    private static boolean looksLikeTar(byte[] data) {
        // tar 头 offset 257 起 6 字节为 "ustar"
        return data.length > 263 && data[257] == 'u' && data[258] == 's' && data[259] == 't'
                && data[260] == 'a' && data[261] == 'r';
    }

    private static String stripCompressSuffix(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".tgz")) {
            return fileName.substring(0, fileName.length() - 4) + ".tar";
        }
        if (lower.endsWith(".gz")) {
            return fileName.substring(0, fileName.length() - 3);
        }
        if (lower.endsWith(".bz2")) {
            return fileName.substring(0, fileName.length() - 4);
        }
        return fileName;
    }

    // ==================== 7z ====================

    private void extract7z(byte[] bytes, int depth, ExtractState state) throws IOException {
        try (SevenZFile sz = new SevenZFile(new SeekableInMemoryByteChannel(bytes))) {
            for (SevenZArchiveEntry entry : sz.getEntries()) {
                if (entry.isDirectory()) {
                    continue;
                }
                String path = sanitizeEntryName(entry.getName());
                if (null == path) {
                    continue;
                }
                // 压缩比闸先过（解压前），再流式读取过实际量闸与总量闸
                guardSize(entry.getSize(), path, state);
                try (InputStream in = sz.getInputStream(entry)) {
                    readGuarded(in, entry.getSize(), path, state);
                }
                accept(path, state.lastData, depth, state);
            }
        }
    }

    // ==================== 公共流程 ====================

    /**
     * 条目落地：计数/护栏检查后，压缩包子条目递归展开（深度允许时），其余进入结果集。
     */
    private void accept(String path, byte[] data, int depth, ExtractState state) {
        state.entryCount++;
        if (state.entryCount > state.maxEntries) {
            throw new GuardException("条目数超过上限 " + state.maxEntries);
        }
        if (isAllowedArchiveExt(path)) {
            if (depth < cfg().getMaxDepth()) {
                // 嵌套压缩包：深度允许则递归展开，子条目路径带上父级前缀便于溯源
                ArchiveExtractResult nested = extract(path, data, depth + 1);
                String prefix = path + "/";
                for (ExtractedEntry e : nested.entries()) {
                    state.entryCount++;
                    state.entries.add(new ExtractedEntry(prefix + e.path(), e.data()));
                }
                for (String f : nested.failures()) {
                    state.failures.add(f);
                }
            } else {
                state.failures.add(path + " - 嵌套压缩包超过深度限制(max-depth=" + cfg().getMaxDepth() + ")，已跳过");
            }
            return;
        }
        state.entries.add(new ExtractedEntry(path, data));
    }

    /**
     * CVE-2026-24770 同款消毒：条目名只用于标题展示与扩展名判断，
     * 永不参与磁盘路径；不合法名返回 null（跳过），含 {@code ..}/绝对路径/盘符/NUL 的拒绝。
     */
    static String sanitizeEntryName(String rawName) {
        if (StringUtils.isBlank(rawName)) {
            return null;
        }
        String name = rawName.replace('\\', '/').trim();
        // __MACOSX 元数据与 .DS_Store 对知识库毫无价值
        if (name.contains("__MACOSX/") || name.endsWith("/.DS_Store") || ".DS_Store".equals(name)) {
            return null;
        }
        // 绝对路径 / 盘符 / NUL 一律拒绝（先判后剥，剥了就漏判）
        if (name.startsWith("/") || name.contains(":") || name.contains("\u0000")) {
            log.warn("Archive entry name rejected (absolute or drive-letter path):{}", rawName);
            return null;
        }
        while (name.startsWith("/")) {
            name = name.substring(1);
        }
        if (name.isBlank()) {
            return null;
        }
        for (String seg : name.split("/")) {
            if ("..".equals(seg)) {
                log.warn("Archive entry name rejected (path traversal):{}", rawName);
                return null;
            }
        }
        // 兼容 zip 包内目录条目形式的「a/b/」——按文件名处理时去掉尾斜杠
        while (name.endsWith("/")) {
            name = name.substring(0, name.length() - 1);
        }
        return name.isBlank() ? null : name;
    }

    private void readGuarded(InputStream in, long declaredSize, String path, ExtractState state) throws IOException {
        guardSize(declaredSize, path, state);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[64 * 1024];
        int n;
        long read = 0;
        while ((n = in.read(buf)) > 0) {
            read += n;
            if (read > state.maxTotalBytes) {
                throw new GuardException("条目解压后超过总量上限 " + (state.maxTotalBytes / 1024 / 1024) + "MB: " + path);
            }
            bos.write(buf, 0, n);
        }
        // zip/tar 流式头的 declared size 可被伪造，实际读到的量同样过闸
        guardSize(read, path, state);
        state.lastData = bos.toByteArray();
        state.totalBytes += state.lastData.length;
        if (state.totalBytes > state.maxTotalBytes) {
            throw new GuardException("解压总量超过上限 " + (state.maxTotalBytes / 1024 / 1024) + "MB");
        }
    }

    /** 单条目护栏：大小闸 + 压缩比闸（宣称比 max-ratio 倍即中止，zip bomb 标准防线） */
    private void guardSize(long size, String path, ExtractState state) {
        if (size <= 0) {
            return;
        }
        if (size > state.maxTotalBytes) {
            throw new GuardException("条目解压后大小超过总量上限: " + path);
        }
        if (state.archiveBytes > 0 && size / (double) state.archiveBytes > state.maxRatio) {
            throw new GuardException("条目压缩比超过 " + state.maxRatio + ": " + path);
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (null != t.getCause() && t.getCause() != t) {
            t = t.getCause();
        }
        return StringUtils.defaultIfBlank(t.getMessage(), t.getClass().getSimpleName());
    }

    private static java.nio.charset.Charset gbkCharset() {
        return java.nio.charset.Charset.forName("GBK");
    }

    /** 护栏触发：中止整包解压（与单条目跳过相对） */
    private static class GuardException extends RuntimeException {
        GuardException(String message) {
            super(message);
        }
    }

    /** 单次 extract 调用的可变状态（配置快照 + 累计计数） */
    private class ExtractState {
        final List<ExtractedEntry> entries = new ArrayList<>();
        final List<String> failures = new ArrayList<>();
        final long maxTotalBytes;
        final int maxEntries;
        final double maxRatio;
        final long archiveBytes;
        int entryCount = 0;
        long totalBytes = 0;
        byte[] lastData;

        ExtractState(long archiveBytes) {
            Parse.Archive c = cfg();
            this.maxTotalBytes = (long) c.getMaxTotalUncompressedMb() * 1024 * 1024;
            this.maxEntries = c.getMaxEntries();
            this.maxRatio = c.getMaxRatio();
            this.archiveBytes = archiveBytes;
        }

        void resetFrom(ExtractState other) {
            entries.clear();
            failures.clear();
            entries.addAll(other.entries);
            failures.addAll(other.failures);
            entryCount = other.entryCount;
            totalBytes = other.totalBytes;
            lastData = other.lastData;
        }
    }

    /**
     * 内存字节 MultipartFile：压缩包解出的条目以此复用 uploadDoc 全链路
     * （saveFile → LocalFileUtil.saveToLocal → transferTo，故 transferTo 必须真实落盘）。
     */
    public static class EntryMultipartFile implements MultipartFile {

        private final String originalFilename;
        private final byte[] content;

        public EntryMultipartFile(String originalFilename, byte[] content) {
            this.originalFilename = originalFilename;
            this.content = content;
        }

        @Override
        @NonNull
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        @NonNull
        public String getContentType() {
            return "application/octet-stream";
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content;
        }

        @Override
        @NonNull
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(@NonNull File dest) throws IOException {
            Files.write(dest.toPath(), content);
        }
    }
}
