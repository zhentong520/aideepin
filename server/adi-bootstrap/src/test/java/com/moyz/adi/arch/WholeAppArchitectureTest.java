package com.moyz.adi.arch;

import com.moyz.adi.common.util.SpringUtil;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.containAnyMethodsThat;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * S1-01（2026-10-27）跨模块架构护栏 —— "先立规矩，后搬家"（为 S1-02 SpringUtil 清零 / S1-04 删 common-biz 提供安全网）。
 *
 * <p>为什么放在 adi-bootstrap：它是唯一聚合全部域模块的模块，测试 classpath 能看到全部业务类。
 * 注意职责边界——单模块内部的包级依赖边基线由 {@code adi-common} 的 AdiPackageEdgeBaselineTest 负责（OPT-15），
 * 本类只管<b>跨模块</b>与<b>跨切面</b>规则。
 *
 * <p>与 OPT-15 现状的适配：5 个域模块（draw/model/kb-rag/chat-core/workflow）的类暂仍在
 * {@code com.moyz.adi.common..} 包名下（迁移批次未到改包名），因此"域间单向依赖"的第一版
 * 用可证明的两条禁令表达：<br>
 * ① 下层（common/common-biz）禁止依赖上层（chat/admin/bootstrap）——上行依赖零容忍；<br>
 * ② controller 层唯一入口：controller 类只允许被 controller 访问，禁止被 service/工具类反向引用。<br>
 * 待 OPT-15 各域搬出并改包名后，本类再逐批升级为显式 DAG 规则（见实施方案 S1-01 卡）。
 *
 * <p>冻结基线管理：SpringUtil.getBean 存量约 37 文件用 FreezingArchRule 入库存档
 * （src/test/resources/archunit-store，必须提交进 git）。新增任何 getBean 调用 = 红。
 * S1-02 清零完成后重跑 refreeze 收紧为零。
 */
@AnalyzeClasses(packages = "com.moyz.adi", importOptions = ImportOption.DoNotIncludeTests.class)
class WholeAppArchitectureTest {

    /**
     * 规则一：controller 唯一入口。
     * controller（含 user/admin/ext 三端）只能被同层访问；service、工具类、任务回调等
     * 反向引用 controller 视为分层违规（把 HTTP 层语义漏进业务层）。
     */
    @ArchTest
    static final ArchRule controllerUniqueEntry = noClasses()
            .that().resideOutsideOfPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..controller..")
            .allowEmptyShould(true)
            .because("controller 层是 HTTP 语义的唯一入口，业务层反向依赖会把 web 概念漏进 service（S1-01）");

    /**
     * 规则二：controller 不得直查 mapper。
     * 数据访问必须经过 service/mapper 中间层，防止在 controller 里拼查询逻辑。
     */
    @ArchTest
    static final ArchRule controllerMustNotTouchMapper = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..mapper..")
            .allowEmptyShould(true)
            .because("数据访问必须收敛到 service 层，controller 直查 mapper 绕过业务校验（S1-01）");

    /**
     * 规则三：上行依赖禁令（第一版"域间单向依赖"的可执行形式）。
     * common / common-biz（及仍留在 common 包名的各域模块）禁止依赖
     * chat / admin / bootstrap——下层不认识上层，这是 OPT-15 搬迁期间不可回退的底线。
     * S1-04 删除 common-biz 后此规则继续守卫剩余 common 代码。
     */
    @ArchTest
    static final ArchRule lowerLayerMustNotReachUpper = noClasses()
            .that().resideInAnyPackage("com.moyz.adi.common..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.moyz.adi.chat..",
                    "com.moyz.adi.admin..",
                    "com.moyz.adi.bootstrap..",
                    "com.moyz.adi")
            .allowEmptyShould(true)
            .because("下层模块禁止依赖上层（chat/admin/bootstrap），OPT-15 单向依赖底线（S1-01）");

    /** SpringUtil#getBean(String,Class) / getBean(Class) 两个静态重载的调用点。 */
    private static final DescribedPredicate<JavaMethodCall> SPRING_UTIL_GET_BEAN =
            new DescribedPredicate<>("call SpringUtil#getBean*") {
                @Override
                public boolean test(JavaMethodCall call) {
                    return SpringUtil.class.getName().equals(call.getTargetOwner().getName())
                            && call.getName().startsWith("getBean");
                }
            };

    /**
     * 规则四：SpringUtil.getBean 服务定位器冻结。
     * 存量约 37 文件（40+ 处调用）已冻结入库存档；从冻结日起<b>新增</b>任何
     * SpringUtil.getBean* 调用 = 失败。改造必须走构造器注入（S1-02 分 3 个 PR 清零）。
     */
    @ArchTest
    static final ArchRule noNewSpringUtilServiceLocator = FreezingArchRule.freeze(
            noClasses()
                    .should().callMethodWhere(SPRING_UTIL_GET_BEAN)
                    .because("服务定位器绕过模块边界与依赖注入，S1-01 冻结存量、S1-02 清零；新代码一律构造器注入"));

    /**
     * 规则五：@Scheduled 必须配 @SchedulerLock（S1-07a）。
     * 多实例共库部署时，没有 ShedLock 的定时任务会在每个实例重复执行。
     * 写法用「含 @Scheduled 方法的类必须也含 @SchedulerLock 方法」——同一方法双注解即可满足。
     * 新增定时任务漏加锁 = 此规则变红。
     */
    @ArchTest
    static final ArchRule scheduledTasksMustCarrySchedulerLock = classes()
            .that(containAnyMethodsThat(annotatedWith(Scheduled.class)))
            .should(new ArchCondition<JavaClass>("annotate every @Scheduled method with @SchedulerLock") {
                @Override
                public void check(JavaClass clazz, ConditionEvents events) {
                    for (var method : clazz.getMethods()) {
                        if (!method.isAnnotatedWith(Scheduled.class)
                                || method.isAnnotatedWith(SchedulerLock.class)) {
                            continue;
                        }
                        events.add(SimpleConditionEvent.violated(clazz,
                                clazz.getName() + "#" + method.getName()
                                        + " 有 @Scheduled 但缺 @SchedulerLock（S1-07a，多实例会重复执行）"));
                    }
                }
            })
            .because("多实例部署时 @Scheduled 缺 @SchedulerLock 会跨实例重复执行（S1-07a）；"
                    + "lockAtMostFor 至少取最长执行时间的 2 倍");
}