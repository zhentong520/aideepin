package com.moyz.adi.common.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import jakarta.annotation.Resource;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ARCH-02：Bean 循环依赖静态检测（防「应用起不来」进主干）。
 *
 * <p>为什么需要：2026-10-21 真机启动 aideepin 时发现——T9-01 交付的
 * {@code WorkflowTriggerService ⇄ WorkflowTriggerRunner} 互注入形成 Bean 循环依赖，
 * Spring 直接 {@code APPLICATION FAILED TO START}，而 CI 只跑单测（不起 Spring 上下文），
 * 缺陷一路进到主干才暴露。
 *
 * <p>本测试用静态扫描代替真实上下文：CI 无需 DB/Redis/Docker，任何环境都能跑，
 * 代价极低，能在提交阶段就把循环依赖挡住。
 *
 * <p>判定口径：
 * <ul>
 *   <li>扫描 {@code com.moyz.adi.common} 下所有 {@code @Component/@Service} 类</li>
 *   <li>边 = {@code @Resource} / {@code @Autowired} 字段注入（项目统一字段注入风格）</li>
 *   <li>忽略自注入（Spring 允许 self reference）</li>
 *   <li>忽略 {@code @Lazy} 标注的边（{@code @Lazy} 是 Spring 官方用于打破循环的手段，
 *       刻意保留这种用法不误报）</li>
 *   <li>接口类型字段跳过（同一接口可能有多实现，静态无法确定目标，误报风险高）</li>
 * </ul>
 */
class BeanCircularDependencyTest {

    private static final String BASE_PACKAGE = "com.moyz.adi.common";

    @Test
    @DisplayName("com.moyz.adi.common 下不存在 Bean 循环依赖（字段注入）")
    void noCircularDependency() {
        Map<Class<?>, Set<Class<?>>> graph = buildInjectionGraph();

        List<List<Class<?>>> cycles = findCycles(graph);
        if (!cycles.isEmpty()) {
            StringBuilder sb = new StringBuilder("检测到 Bean 循环依赖（会导致 Spring 启动失败）：\n");
            for (List<Class<?>> cycle : cycles) {
                sb.append("  ");
                for (int i = 0; i < cycle.size(); i++) {
                    sb.append(cycle.get(i).getSimpleName());
                    if (i < cycle.size() - 1) {
                        sb.append(" -> ");
                    }
                }
                sb.append('\n');
            }
            sb.append("修复方向：把其中一方的依赖改为单向（职责下沉或延迟注入），禁止用 @Lazy 掩盖设计问题。");
        }

        assertTrue(cycles.isEmpty(), () -> "发现 Bean 循环依赖：\n" + renderCycles(cycles));
    }

    /** 构建注入依赖图：key=被注入的类，value=它注入的类集合 */
    private Map<Class<?>, Set<Class<?>>> buildInjectionGraph() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter((TypeFilter) new AnnotationTypeFilter(Component.class));

        Map<Class<?>, Set<Class<?>>> graph = new HashMap<>();
        for (org.springframework.beans.factory.config.BeanDefinition def :
                scanner.findCandidateComponents(BASE_PACKAGE)) {
            Class<?> clazz;
            try {
                clazz = ClassUtils.forName(def.getBeanClassName(), getClass().getClassLoader());
            } catch (Throwable e) {
                // 类加载不了的（缺依赖等）跳过，不在本测试职责内
                continue;
            }
            if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
                continue;
            }
            Set<Class<?>> targets = new HashSet<>();
            for (Field field : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (!isInjectedField(field)) {
                    continue;
                }
                if (hasLazyAnnotation(field)) {
                    // @Lazy 是显式允许的打破循环手段，不计入环检测
                    continue;
                }
                Class<?> target = field.getType();
                if (target == clazz) {
                    // 自注入：Spring 支持 self reference，不算环
                    continue;
                }
                if (target.isInterface()) {
                    // 接口注入：静态无法确定唯一实现，跳过以免误报
                    continue;
                }
                targets.add(target);
            }
            graph.put(clazz, targets);
        }
        return graph;
    }

    private boolean isInjectedField(Field field) {
        for (Annotation annotation : field.getAnnotations()) {
            Class<? extends Annotation> type = annotation.annotationType();
            if (Resource.class.equals(type)
                    || "org.springframework.beans.factory.annotation.Autowired".equals(type.getName())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasLazyAnnotation(Field field) {
        return field.getAnnotation(Lazy.class) != null;
    }

    /** DFS 找所有环（去重：按环中最小的类名做规范表示） */
    private List<List<Class<?>>> findCycles(Map<Class<?>, Set<Class<?>>> graph) {
        List<List<Class<?>>> cycles = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (Class<?> start : graph.keySet()) {
            Deque<Class<?>> path = new ArrayDeque<>();
            Set<Class<?>> onPath = new HashSet<>();
            dfs(start, graph, path, onPath, cycles, seen);
        }
        return cycles;
    }

    private void dfs(Class<?> current, Map<Class<?>, Set<Class<?>>> graph, Deque<Class<?>> path,
                      Set<Class<?>> onPath, List<List<Class<?>>> cycles, Set<String> seen) {
        if (onPath.contains(current)) {
            List<Class<?>> cycle = new ArrayList<>();
            boolean collecting = false;
            for (Class<?> c : path) {
                if (c == current) {
                    collecting = true;
                }
                if (collecting) {
                    cycle.add(c);
                }
            }
            if (!cycle.isEmpty()) {
                cycle.add(current);
                String key = canonicalKey(cycle);
                if (seen.add(key)) {
                    cycles.add(cycle);
                }
            }
            return;
        }
        path.addLast(current);
        onPath.add(current);
        Set<Class<?>> targets = graph.get(current);
        if (null != targets) {
            for (Class<?> target : targets) {
                if (graph.containsKey(target)) {
                    dfs(target, graph, path, onPath, cycles, seen);
                }
            }
        }
        onPath.remove(current);
        path.removeLast();
    }

    /** 环的规范表示（起点选类名最小的那个），用于去重 */
    private String canonicalKey(List<Class<?>> cycle) {
        List<Class<?>> body = new ArrayList<>(cycle.subList(0, cycle.size() - 1));
        if (body.isEmpty()) {
            return String.valueOf(cycle);
        }
        int minIdx = 0;
        for (int i = 1; i < body.size(); i++) {
            if (body.get(i).getSimpleName().compareTo(body.get(minIdx).getSimpleName()) < 0) {
                minIdx = i;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < body.size(); i++) {
            sb.append(body.get((minIdx + i) % body.size()).getName()).append('>');
        }
        return sb.toString();
    }

    private String renderCycles(List<List<Class<?>>> cycles) {
        if (cycles.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (List<Class<?>> cycle : cycles) {
            sb.append('\n');
            for (Class<?> c : cycle) {
                sb.append("  ").append(c.getSimpleName()).append(" -> ");
            }
        }
        return sb.toString();
    }

    /** 供排查用：打印当前图（调试用，不参与断言） */
    @Test
    @DisplayName("调试：打印注入依赖图")
    void printGraph() {
        Map<Class<?>, Set<Class<?>>> graph = buildInjectionGraph();
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Class<?>, Set<Class<?>>> entry : graph.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            sb.append(entry.getKey().getSimpleName()).append(" -> ");
            sb.append(new TreeSet<>(entry.getValue().stream().map(Class::getSimpleName).toList()));
            sb.append('\n');
        }
        System.out.println("[bean-graph]\n" + sb);
    }
}
