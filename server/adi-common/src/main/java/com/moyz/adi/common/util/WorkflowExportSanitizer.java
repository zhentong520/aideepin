package com.moyz.adi.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.dto.workflow.WfExportResp;
import com.moyz.adi.common.dto.workflow.WfNodeDto;

import java.util.Iterator;
import java.util.Map;

/**
 * T9-06：工作流导出脱敏。⚠️ 导出前必须剥离敏感凭据类字段：
 * <ul>
 *   <li>nodeConfig 内键名命中敏感特征（api key / password / secret /
 *       token / authorization，忽略大小写与中划线）的字段<b>递归剥离</b>——
 *       典型如 MailSend 节点 sender.password（listDtoByWfId 解密后是明文！）；</li>
 *   <li>节点级 credentialUuid 剥离：凭据库引用跨环境无意义且不应外泄；</li>
 *   <li>知识检索节点的 knowledge_base_uuid 属于业务引用<b>不剥离</b>（T3-14 语义：
 *       导入方按 uuid 校验，缺失即报错）。</li>
 * </ul>
 * 快照（T9-05）走同一份 definition 结构但<b>不经过本类</b>——回滚需要真实配置。
 */
public final class WorkflowExportSanitizer {

    private WorkflowExportSanitizer() {
    }

    /**
     * 原地脱敏导出对象：节点 credentialUuid 置空 + nodeConfig 递归剥离敏感键。
     */
    public static void sanitize(WfExportResp resp) {
        if (null == resp || null == resp.getNodes()) {
            return;
        }
        for (WfNodeDto node : resp.getNodes()) {
            node.setCredentialUuid(null);
            if (null != node.getNodeConfig()) {
                node.setNodeConfig((ObjectNode) strip(node.getNodeConfig()));
            }
        }
    }

    /**
     * 递归剥离敏感键（测试可直接调用）。
     */
    public static JsonNode strip(JsonNode node) {
        if (null == node) {
            return null;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> iterator = object.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> field = iterator.next();
                if (isSensitiveKey(field.getKey())) {
                    iterator.remove();
                } else {
                    strip(field.getValue());
                }
            }
            return object;
        }
        if (node.isArray()) {
            node.forEach(WorkflowExportSanitizer::strip);
        }
        return node;
    }

    /**
     * 键名是否敏感（忽略大小写、中划线/下划线分隔）。包级静态便于单测。
     */
    static boolean isSensitiveKey(String key) {
        if (null == key || key.isEmpty()) {
            return false;
        }
        String normalized = key.toLowerCase().replace('-', '_');
        return normalized.contains("api_key")
                || normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("token")
                || normalized.contains("authorization");
    }
}
