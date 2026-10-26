package com.moyz.adi.common.workflow.node.database;

import com.moyz.adi.common.entity.WorkflowComponent;
import com.moyz.adi.common.entity.WorkflowNode;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.service.DbDatasourceService;
import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.util.SpringUtil;
import com.moyz.adi.common.workflow.NodeProcessResult;
import com.moyz.adi.common.workflow.WfNodeState;
import com.moyz.adi.common.workflow.WfState;
import com.moyz.adi.common.workflow.WorkflowUtil;
import com.moyz.adi.common.workflow.data.NodeIOData;
import com.moyz.adi.common.workflow.node.AbstractWfNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.moyz.adi.common.workflow.WfConstant.DEFAULT_OUTPUT_PARAM_NAME;

/**
 * 工作流数据库节点：在外部数据源上执行只读 SQL 并把结果传给下游。
 * <p>
 * Workflow database node — executes read-only SQL on an external datasource
 * and passes the result downstream.
 * </p>
 */
@Slf4j
public class DatabaseNode extends AbstractWfNode {

    public DatabaseNode(WorkflowComponent wfComponent, WorkflowNode node, WfState wfState, WfNodeState nodeState) {
        super(wfComponent, node, wfState, nodeState);
    }

    @Override
    protected NodeProcessResult onProcess() {
        List<NodeIOData> outputData = new ArrayList<>();
        DatabaseNodeConfig nodeConfig = checkAndGetConfig(DatabaseNodeConfig.class);
        DbDatasourceService dbDatasourceService = SpringUtil.getBean(DbDatasourceService.class);

        String sql = nodeConfig.getSql();
        if (StringUtils.isBlank(sql)) {
            throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_, "database node sql is empty");
        }
        if (StringUtils.isBlank(nodeConfig.getDatasourceUuid())) {
            throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_, "database node datasource is empty");
        }
        if (Boolean.TRUE.equals(nodeConfig.getReplaceInput())) {
            sql = sql.replace("{{input}}", getFirstInputText());
        }
        // 统一到工作流的 {name} 占位符约定：SQL 里可以直接引用某个上游具名参数（如 {city}）。
        // 顺序很关键——双花括号的 {{input}} 必须先被替换掉，否则 renderTemplate 会在其内部
        // 匹配到 "{input}" 并把结果包成 "{值}"，拼出非法 SQL。
        // Apply the workflow-wide {name} placeholder convention on top of the legacy {{input}} form.
        // Order matters: {{input}} must be substituted first, otherwise renderTemplate would match
        // the inner "{input}" and wrap the value in braces.
        sql = WorkflowUtil.renderTemplate(sql, state.getInputs());
        //Workflow nodes run on @Async threads: pass the user explicitly instead of relying on ThreadContext
        com.moyz.adi.common.entity.User user = getWfState().getUser();
        if (null == user) {
            throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_, "database node cannot resolve the workflow user");
        }
        Map<String, Object> queryResult = dbDatasourceService.queryForUser(
                nodeConfig.getDatasourceUuid(), sql, nodeConfig.getLimit(), user.getId());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) queryResult.get("rows");
        String content = "json".equalsIgnoreCase(nodeConfig.getOutputFormat())
                ? JsonUtil.toJson(null == rows ? List.of() : rows)
                : renderText(rows);

        outputData.add(NodeIOData.createByText(DEFAULT_OUTPUT_PARAM_NAME, "数据库查询结果", content));
        outputData.add(NodeIOData.createByText("row_count", "行数", String.valueOf(queryResult.get("count"))));
        return NodeProcessResult.builder().content(outputData).build();
    }

    private String renderText(List<Map<String, Object>> rows) {
        if (null == rows || rows.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> row : rows) {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                parts.add(entry.getKey() + ": " + (null == entry.getValue() ? "" : entry.getValue()));
            }
            sb.append(String.join("; ", parts)).append("\n");
        }
        return sb.toString();
    }
}
