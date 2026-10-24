package com.moyz.adi.common.arch;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * OPT-15 第 2 步「规则先于搬迁」（2026-10-24）。
 *
 * <p>替换思路：ArchUnit 的 {@code slices().beFreeOfCycles()} 报告的 cycle 子集在
 * 类图相同的情况下仍非确定（两次运行枚举出不同 cycle 集合，实测 100 处违规两轮内容不同），
 * 无法作为 FreezingArchRule 的稳定基线。这里改为<b>确定性的包级依赖边基线</b>：
 * import 关系导出的「包 → 包」边集合是固定的，与枚举顺序无关。
 *
 * <p>规则：基线（src/test/resources/package-edges-baseline.txt，一行一条 "from -> to"）
 * 之外<b>新增</b>的包间依赖边 = 红；边消失（依赖被清除）则忽略，允许改善。
 * 模块化推进时按批重生成基线并人工 review diff。
 */
class AdiPackageEdgeBaselineTest {

    private static final String BASELINE = "/package-edges-baseline.txt";

    @Test
    void noNewPackageLevelDependencies() throws IOException {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.moyz.adi.common");

        Set<String> current = new TreeSet<>();
        for (JavaClass c : classes) {
            String from = topPackage(c.getPackageName());
            if (from == null) {
                continue;
            }
            c.getDirectDependenciesFromSelf().forEach(dep -> {
                String to = topPackage(dep.getTargetClass().getPackageName());
                if (to != null && !to.equals(from)) {
                    current.add(from + " -> " + to);
                }
            });
        }

        Set<String> baseline = new TreeSet<>();
        try (InputStream in = getClass().getResourceAsStream(BASELINE)) {
            if (in == null) {
                fail("baseline file missing: " + BASELINE);
            }
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                String t = line.trim();
                if (!t.isEmpty() && !t.startsWith("#")) {
                    baseline.add(t);
                }
            }
        }

        Set<String> added = new TreeSet<>(current);
        added.removeAll(baseline);
        if (!added.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("OPT-15: ").append(added.size())
                    .append(" NEW package-level dependency edge(s) not in the frozen baseline.\n")
                    .append("If intentional (e.g. mid-refactor), review them and regenerate the baseline:\n")
                    .append("  mvn -pl adi-common test -Dtest=AdiPackageEdgeBaselineTest -Dadi.edges.dump=<file>\n");
            for (String e : added) {
                sb.append("  + ").append(e).append('\n');
            }
            fail(sb.toString());
        }
        assertTrue(true);
    }

    /** 基线再生：-Dadi.edges.dump=<file> 时把当前全量边集写出（人工 review 后入库）。 */
    @Test
    void dumpEdgesWhenRequested() throws IOException {
        String dump = System.getProperty("adi.edges.dump");
        org.junit.jupiter.api.Assumptions.assumeTrue(dump != null && !dump.isBlank());
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.moyz.adi.common");
        Map<String, Set<String>> byFrom = new TreeMap<>();
        for (JavaClass c : classes) {
            String from = topPackage(c.getPackageName());
            if (from == null) {
                continue;
            }
            c.getDirectDependenciesFromSelf().forEach(dep -> {
                String to = topPackage(dep.getTargetClass().getPackageName());
                if (to != null && !to.equals(from)) {
                    byFrom.computeIfAbsent(from, k -> new TreeSet<>()).add(to);
                }
            });
        }
        StringBuilder sb = new StringBuilder("# OPT-15 package-level dependency edges (regenerate: -Dadi.edges.dump=<file>)\n");
        byFrom.forEach((from, tos) -> tos.forEach(to -> sb.append(from).append(" -> ").append(to).append('\n')));
        java.nio.file.Files.writeString(java.nio.file.Path.of(dump), sb.toString(), StandardCharsets.UTF_8);
        System.out.println("[AdiPackageEdgeBaseline] dumped " + byFrom.size() + " from-packages -> " + dump);
    }

    /** 一级子包名（com.moyz.adi.common 之下第一段）；域外包返回 null（不纳入基线）。 */
    private static String topPackage(String pkg) {
        if (pkg == null) {
            return null;
        }
        String prefix = "com.moyz.adi.common.";
        if (!pkg.startsWith(prefix)) {
            return null;
        }
        String rest = pkg.substring(prefix.length());
        return rest.isEmpty() ? "(root)" : rest.split("\\.")[0];
    }
}
