package com.moyz.adi.common.dto.workflow;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * T3-13：指定节点可用变量（供前端变量穿梭框）。
 * <p>
 * name 为节点输入参数名（refInput.name），可在模板中作为 {@code {{name.path}}} 的根变量；
 * 有历史成功执行数据时给 example 示例值，对象类型递归展开 children 至 {@code maxDepth} 层，
 * 控制响应体量。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WfAvailableVarDto {
    /** 变量名（模板根变量） */
    private String name;
    /** 引用来源节点 uuid */
    private String sourceNodeUuid;
    /** 引用来源节点标题 */
    private String sourceNodeTitle;
    /** 引用来源节点的输出参数名 */
    private String sourceParamName;
    /** 类型：text / number / bool / files / options / object / array / unknown */
    private String type;
    /** 示例值（来自最近一次成功执行的输出；无历史时为 null） */
    private Object example;
    /** 对象/数组类型展开的子字段（递归最多 2 层） */
    private List<WfAvailableVarDto> children;
}
