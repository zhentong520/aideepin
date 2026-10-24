package com.moyz.adi.common.service;

import com.moyz.adi.common.dto.workflow.WfExportResp;
import com.moyz.adi.common.dto.workflow.WorkflowResp;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.util.JsonUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * T9-06：内置官方工作流模板（classpath:wf-template/*.json）。
 * <ul>
 *   <li>模板 JSON 与导出 JSON 同构（WfExportResp），新建复用 {@link WorkflowService#importWorkflow}
 *       全链路（组件按名重映射 / 名字冲突加后缀 / 未知节点标记 / 知识包校验）；</li>
 *   <li>模板文件不含任何运行数据与敏感凭据（维护约定：新增模板直接放资源文件，不改代码）。</li>
 * </ul>
 */
@Slf4j
@Service
public class WorkflowTemplateService {

    private static final String TEMPLATE_DIR = "classpath*:wf-template/*.json";

    @Lazy
    @Resource
    private WorkflowService workflowService;

    /**
     * 模板清单（id = 文件名去扩展名），新建工作流时前端下拉可选。
     */
    public List<WfTemplateInfo> list() {
        List<WfTemplateInfo> result = new ArrayList<>();
        for (org.springframework.core.io.Resource resource : loadResources()) {
            try {
                WfExportResp template = JsonUtil.fromJson(resource.getContentAsString(StandardCharsets.UTF_8), WfExportResp.class);
                if (null == template) {
                    continue;
                }
                result.add(new WfTemplateInfo(templateIdOf(resource), template.getTitle(), template.getRemark()));
            } catch (IOException e) {
                log.error("Failed to load workflow template: {}", resource.getFilename(), e);
            }
        }
        return result;
    }

    /**
     * 从模板新建工作流：加载模板 JSON → 覆盖 title/remark → 走导入链路
     * （名字冲突自动加后缀 / 组件重映射 / 未知节点标记）。模板不存在抛 A_WF_TEMPLATE_NOT_FOUND。
     */
    public WorkflowResp createFromTemplate(String templateId, String title, String remark) {
        if (StringUtils.isBlank(templateId) || StringUtils.isBlank(title)) {
            throw new BaseException(ErrorEnum.A_PARAMS_ERROR);
        }
        WfExportResp template = load(templateId);
        template.setTitle(title.trim());
        template.setRemark(remark);
        return workflowService.importWorkflow(template);
    }

    /**
     * 加载模板 JSON（不存在抛 A_WF_TEMPLATE_NOT_FOUND）。
     */
    public WfExportResp load(String templateId) {
        for (org.springframework.core.io.Resource resource : loadResources()) {
            if (templateId.equals(templateIdOf(resource))) {
                try {
                    WfExportResp template = JsonUtil.fromJson(resource.getContentAsString(StandardCharsets.UTF_8), WfExportResp.class);
                    if (null != template) {
                        return template;
                    }
                } catch (IOException e) {
                    log.error("Failed to load workflow template: {}", resource.getFilename(), e);
                }
                break;
            }
        }
        throw new BaseException(ErrorEnum.A_WF_TEMPLATE_NOT_FOUND, templateId);
    }

    private List<org.springframework.core.io.Resource> loadResources() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            return List.of(resolver.getResources(TEMPLATE_DIR));
        } catch (IOException e) {
            log.error("Failed to scan workflow template dir: {}", TEMPLATE_DIR, e);
            return List.of();
        }
    }

    private String templateIdOf(org.springframework.core.io.Resource resource) {
        String filename = resource.getFilename();
        if (null != filename && filename.contains(".")) {
            return filename.substring(0, filename.lastIndexOf('.'));
        }
        return filename;
    }

    /**
     * 模板清单项。
     */
    public record WfTemplateInfo(String id, String title, String remark) {
    }
}
