package com.moyz.adi.common.workflow;

import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.constant.AdiConstant;
import com.moyz.adi.common.service.SysConfigService;
import com.moyz.adi.common.util.SpringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * T9-02/03/04/07（铁律 10）：工作流 P3 新节点统一开关读取器。
 * <p>
 * 优先级与 {@code OrchestratorNode#isEnabled} 一致：
 * <ol>
 *   <li>sys_config 热开关（管理端保存即生效，缺省开）；</li>
 *   <li>配置文件 adi.wf.*-enabled（无 Spring 上下文或未配置时回退，缺省开）。</li>
 * </ol>
 * 任一关闭即拒绝执行对应节点；两个层级都不可用时按「开启」处理（与编排节点兜底语义一致）。
 * <p>
 * Unified switch reader for the P3 workflow nodes (T9-02/03/04/07). sys_config hot switch
 * takes precedence over the adi.wf.* property; either being off rejects the node.
 */
@Slf4j
public final class WfFeatureGate {

    private WfFeatureGate() {
    }

    /** T9-02 Loop/Each 循环节点开关 */
    public static boolean loopEnabled() {
        return check(AdiConstant.SysConfigKey.WF_LOOP_ENABLED, AdiProperties.Wf.class, "isLoopEnabled");
    }

    /** T9-03 Parallel 并行节点开关 */
    public static boolean parallelEnabled() {
        return check(AdiConstant.SysConfigKey.WF_PARALLEL_ENABLED, AdiProperties.Wf.class, "isParallelEnabled");
    }

    /** T9-04 Code 代码节点开关 */
    public static boolean codeEnabled() {
        return check(AdiConstant.SysConfigKey.WF_CODE_ENABLED, AdiProperties.Wf.class, "isCodeEnabled");
    }

    /** T9-07 SubWorkflow 子工作流节点开关 */
    public static boolean subWorkflowEnabled() {
        return check(AdiConstant.SysConfigKey.WF_SUB_WORKFLOW_ENABLED, AdiProperties.Wf.class, "isSubWorkflowEnabled");
    }

    /** T9-07 节点 onError 错误分支开关 */
    public static boolean errorBranchEnabled() {
        return check(AdiConstant.SysConfigKey.WF_ERROR_BRANCH_ENABLED, AdiProperties.Wf.class, "isErrorBranchEnabled");
    }

    private static boolean check(String sysKey, Class<AdiProperties.Wf> wfClass, String getter) {
        String sysValue = SysConfigService.getByKey(sysKey);
        if (StringUtils.isNotBlank(sysValue)) {
            return Boolean.parseBoolean(sysValue.trim());
        }
        try {
            AdiProperties props = SpringUtil.getBean(AdiProperties.class);
            return (boolean) wfClass.getMethod(getter).invoke(props.getWf());
        } catch (Exception e) {
            //无 Spring 上下文（单测/启动早期）时按缺省开处理，与 OrchestratorNode 兜底一致
            return true;
        }
    }
}
