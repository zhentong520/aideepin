package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.toolkit.ChainWrappers;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.cosntant.AdiConstant;
import com.moyz.adi.common.dto.UserMcpDto;
import com.moyz.adi.common.dto.mcp.McpCommonParam;
import com.moyz.adi.common.dto.mcp.McpCustomizedParamDefinition;
import com.moyz.adi.common.dto.mcp.UserMcpCustomizedParam;
import com.moyz.adi.common.dto.mcp.UserMcpUpdateReq;
import com.moyz.adi.common.entity.Mcp;
import com.moyz.adi.common.entity.UserMcp;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.mapper.UserMcpMapper;
import com.moyz.adi.common.util.AesUtil;
import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.util.MPPageUtil;
import com.moyz.adi.common.util.PrivilegeUtil;
import com.moyz.adi.common.util.UuidUtil;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.moyz.adi.common.enums.ErrorEnum.A_USER_MCP_SERVER_NOT_FOUND;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toMap;

@Slf4j
@Service
public class UserMcpService extends ServiceImpl<UserMcpMapper, UserMcp> {

    @Resource
    private McpService mcpService;

    public List<UserMcp> searchEnableByUserId(Long userId) {
        return this.lambdaQuery()
                .eq(UserMcp::getUserId, userId)
                .eq(UserMcp::getIsEnable, true)
                .eq(UserMcp::getIsDeleted, false)
                .list();
    }

    /**
     * 获取用户已启用的MCP列表（用于External API）
     * 返回用户已启用的MCP服务信息，不包含敏感配置
     */
    public List<UserMcpDto> searchByUserIdForExtApi(Long userId) {
        List<UserMcp> userMcpList = this.lambdaQuery()
                .eq(UserMcp::getUserId, userId)
                .eq(UserMcp::getIsEnable, true)
                .eq(UserMcp::getIsDeleted, false)
                .list();

        List<UserMcpDto> dtoList = new ArrayList<>();

        List<Mcp> mcpList = new ArrayList<>();
        if (!userMcpList.isEmpty()) {
            mcpList = mcpService.listByIds(userMcpList.stream()
                    .map(UserMcp::getMcpId)
                    .distinct()
                    .toList());
        }
        for (UserMcp userMcp : userMcpList) {
            UserMcpDto dto = new UserMcpDto();
            BeanUtils.copyProperties(userMcp, dto);

            Mcp mcp = mcpList.stream()
                    .filter(item -> item.getId().equals(dto.getMcpId()))
                    .findFirst()
                    .orElse(null);
            setMcpInfo(dto, mcp);
            dtoList.add(dto);
        }
        return dtoList;
    }

    /**
     * 当前用户已启用的 MCP 服务（含服务信息，不含敏感配置），供工作流 MCP 节点选择器使用。
     * Enabled MCP servers of the current user with their public info — used by the workflow MCP
     * node's selector. Delegates to the same query as the external-API listing to avoid a second
     * filtering implementation.
     */
    public List<UserMcpDto> listEnabledWithInfo(Long userId) {
        return searchByUserIdForExtApi(userId);
    }

    public Page<UserMcpDto> searchByUserId(Long userId, Integer currentPage, Integer pageSize) {
        Page<UserMcp> page = this.lambdaQuery()
                .eq(UserMcp::getUserId, userId)
                .eq(UserMcp::getIsDeleted, false)
                .orderByDesc(UserMcp::getUpdateTime)
                .page(new Page<>(currentPage, pageSize));

        List<UserMcpDto> dtoList = new ArrayList<>();

        List<Mcp> mcpList = new ArrayList<>();
        if (!page.getRecords().isEmpty()) {
            mcpList = mcpService.listByIds(page.getRecords().stream()
                    .map(UserMcp::getMcpId)
                    .distinct()
                    .toList());
        }
        for (UserMcp userMcp : page.getRecords()) {
            UserMcpDto dto = new UserMcpDto();
            BeanUtils.copyProperties(userMcp, dto);

            Mcp mcp = mcpList.stream()
                    .filter(item -> item.getId().equals(dto.getMcpId()))
                    .findFirst()
                    .orElse(null);
            setMcpInfo(dto, mcp);
            dtoList.add(dto);
        }
        Page<UserMcpDto> result = MPPageUtil.convertToPage(page, UserMcpDto.class);
        result.setRecords(dtoList);
        return result;
    }

    public UserMcpDto saveOrUpdate(UserMcpUpdateReq editReq) {
        if (null == editReq.getMcpCustomizedParams() && null == editReq.getIsEnable()) {
            log.warn("UserMcp edit request is empty, editReq: {}", editReq);
            return null;
        }
        Mcp mcp = mcpService.getOrThrow(editReq.getMcpId(), false);
        UserMcp userMcp = ChainWrappers.lambdaQueryChain(baseMapper)
                .eq(UserMcp::getMcpId, editReq.getMcpId())
                .eq(UserMcp::getUserId, ThreadContext.getCurrentUserId())
                .eq(UserMcp::getIsDeleted, false)
                .one();
        List<UserMcpCustomizedParam> paramSettings = editReq.getMcpCustomizedParams();
        if (null == userMcp) {
            userMcp = new UserMcp();
            userMcp.setUuid(UuidUtil.createShort());
            userMcp.setMcpId(editReq.getMcpId());
            userMcp.setUserId(ThreadContext.getCurrentUserId());
            if (null != paramSettings) {
                encryptParams(paramSettings, mcp);
                userMcp.setMcpCustomizedParams(paramSettings);
            }
            if (null != editReq.getIsEnable()) {
                userMcp.setIsEnable(editReq.getIsEnable());
            }
            baseMapper.insert(userMcp);
        } else {
            UserMcp updateObj = new UserMcp();
            updateObj.setId(userMcp.getId());
            if (null != paramSettings) {
                encryptParams(paramSettings, mcp);
                updateObj.setMcpCustomizedParams(paramSettings);
            }
            if (null != editReq.getIsEnable()) {
                updateObj.setIsEnable(editReq.getIsEnable());
            }
            // 必须更新「只带改动字段」的 updateObj。以前误传 userMcp（查库得到的原值），
            // 等于把旧数据原样写回，用户新提交的参数与启停状态从未落库。
            // Must update the delta object. Passing the freshly-loaded userMcp wrote the old
            // values back, so newly submitted params/toggle never persisted.
            baseMapper.updateById(updateObj);
        }
        UserMcpDto dto = new UserMcpDto();
        BeanUtils.copyProperties(userMcp, dto);
        setMcpInfo(dto, mcp);
        return dto;
    }

    public List<McpClient> createMcpClients(Long userId, List<Long> mcpIds) {
        return createClientEntries(userId, mcpIds).stream()
                .map(McpClientEntry::client)
                .collect(toCollection(ArrayList::new));
    }

    /**
     * 解析候选 MCP 服务 id：显式给定时用它；为空表示「当前用户全部已启用服务」。
     * 二开新增（2026-09-22）：仅用于工作流的 MCP 节点与工具枚举，
     * 不影响 {@link #createMcpClients} 既有语义（空集合仍返回空，避免改变对话链路行为）。
     * Resolve candidate MCP ids. An empty input means "every MCP server enabled by the current user".
     * Used only by the workflow MCP node and the tool-listing endpoint.
     */
    private List<Long> resolveCandidateMcpIds(Long userId, List<Long> mcpIds) {
        if (null != mcpIds && !mcpIds.isEmpty()) {
            return mcpIds;
        }
        return searchEnableByUserId(userId).stream()
                .map(UserMcp::getMcpId)
                .distinct()
                .toList();
    }

    /**
     * MCP 客户端与其服务元数据的配对 | An MCP client paired with its server metadata.
     * <p>
     * 节点执行需要「工具最后在哪个服务上命中」来记指标，因此不能只返回裸 client。
     * </p>
     */
    public record McpClientEntry(Mcp mcp, McpClient client) {

        public Long mcpId() {
            return mcp.getId();
        }

        public String mcpTitle() {
            return mcp.getTitle();
        }

        public void closeQuietly() {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("Failed to close MCP client, mcpId: {}", mcp.getId(), e);
            }
        }
    }

    /**
     * 当前用户可用的 MCP 工具清单（跨服务展开），供前端选择工具与执行前校验。
     * Tools exposed by the current user's MCP servers, flattened across servers.
     */
    public record McpToolInfo(Long mcpId, String mcpTitle, String toolName, String description, String parameterSchema) {
    }

    /**
     * 展开候选 MCP 服务暴露的工具清单。单个服务不可用时只跳过该服务，不影响其余服务。
     * List tools across the candidate MCP servers; a broken server is skipped, not fatal.
     */
    public List<McpToolInfo> listTools(Long userId, List<Long> mcpIds) {
        List<McpClientEntry> entries = createClientEntries(userId, resolveCandidateMcpIds(userId, mcpIds));
        List<McpToolInfo> result = new ArrayList<>();
        try {
            for (McpClientEntry entry : entries) {
                try {
                    for (ToolSpecification spec : entry.client().listTools()) {
                        result.add(new McpToolInfo(
                                entry.mcpId(),
                                entry.mcpTitle(),
                                spec.name(),
                                spec.description(),
                                null == spec.parameters() ? "" : JsonUtil.toJson(spec.parameters())));
                    }
                } catch (Exception e) {
                    log.error("Failed to list MCP tools, mcpId: {}, title: {}", entry.mcpId(), entry.mcpTitle(), e);
                }
            }
        } finally {
            entries.forEach(McpClientEntry::closeQuietly);
        }
        return result;
    }

    /**
     * MCP 调用结果（含命中的服务信息，供节点记录指标）。
     * Result of a single MCP tool call, including which server actually served it.
     */
    public record McpToolCallResult(Long mcpId, String mcpTitle, String toolName, String resultText) {
    }

    /**
     * 调用一次 MCP 工具并关闭客户端。
     * <p>
     * 二开新增（2026-09-22）：工作流 MCP 节点使用。客户端生命周期（含 stdio 子进程）在本方法内
     * 用 finally 收口，调用方无需关心关闭逻辑；工具的入参校验交给 MCP 服务自身。
     * </p>
     * Invoke one MCP tool and always close the clients. Tool lookup order follows the server list;
     * the first server exposing the tool wins and any additional match is logged.
     */
    public McpToolCallResult callTool(Long userId, List<Long> mcpIds, String toolName, String arguments) {
        List<McpClientEntry> entries = createClientEntries(userId, resolveCandidateMcpIds(userId, mcpIds));
        if (entries.isEmpty()) {
            throw new BaseException(A_USER_MCP_SERVER_NOT_FOUND);
        }
        List<String> matchedServers = new ArrayList<>();
        try {
            for (McpClientEntry entry : entries) {
                List<ToolSpecification> tools;
                try {
                    tools = entry.client().listTools();
                } catch (Exception e) {
                    log.error("Failed to list MCP tools, mcpId: {}, title: {}", entry.mcpId(), entry.mcpTitle(), e);
                    continue;
                }
                boolean hit = tools.stream().anyMatch(item -> toolName.equals(item.name()));
                if (!hit) {
                    continue;
                }
                matchedServers.add(entry.mcpTitle());
                if (matchedServers.size() > 1) {
                    //多个服务同名工具：取列表中的第一个，其余仅记录，避免执行结果不可预期
                    log.warn("MCP tool [{}] is exposed by multiple servers {}, using the first one", toolName, matchedServers);
                    continue;
                }
                ToolExecutionRequest request = ToolExecutionRequest.builder()
                        .name(toolName)
                        .arguments(StringUtils.defaultIfBlank(arguments, "{}"))
                        .build();
                String text = entry.client().executeTool(request).resultText();
                return new McpToolCallResult(entry.mcpId(), entry.mcpTitle(), toolName, StringUtils.defaultString(text));
            }
        } finally {
            entries.forEach(McpClientEntry::closeQuietly);
        }
        throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_,
                "no MCP server exposes the tool: " + toolName
                        + (matchedServers.isEmpty() ? "" : ", checked servers: " + matchedServers));
    }

    private List<McpClientEntry> createClientEntries(Long userId, List<Long> mcpIds) {
        List<McpClientEntry> result = new ArrayList<>();
        if (mcpIds == null || mcpIds.isEmpty()) {
            log.warn("No MCP IDs provided for creating MCP clients.");
            return result;
        }

        //过滤出用户已启用的MCP
        Map<Long, UserMcp> mcpIdToUserMcp = this.lambdaQuery()
                .in(UserMcp::getMcpId, mcpIds)
                .eq(UserMcp::getUserId, userId)
                .eq(UserMcp::getIsDeleted, false)
                .eq(UserMcp::getIsEnable, true)
                .list()
                .stream()
                .collect(toMap(UserMcp::getMcpId, Function.identity(), (a, b) -> a));

        // 查询MCP信息
        List<Mcp> mcpInfos = mcpService.listByIds(mcpIdToUserMcp.keySet().stream().toList(), true);
        for (Mcp mcp : mcpInfos) {
            UserMcp userMcp = mcpIdToUserMcp.get(mcp.getId());
            if (userMcp == null) {
                log.warn("No user MCP params found for MCP ID: {}", mcp.getId());
                continue;
            }

            // 解密用户设置的MCP参数
            decryptParams(userMcp.getMcpCustomizedParams(), mcp);

            McpTransport transport;
            if (AdiConstant.McpConstant.TRANSPORT_TYPE_SSE.equals(mcp.getTransportType())) {
                String httpQueryString = createHttpQueryString(mcp, userMcp);
                String url = buildUrlWithQuery(mcp.getSseUrl(), httpQueryString);
                transport = new HttpMcpTransport.Builder()
                        .sseUrl(url)
                        .timeout(Duration.ofSeconds(mcp.getSseTimeout() > 0 ? mcp.getSseTimeout() : 30))
                        .logRequests(true)
                        .logResponses(true)
                        .build();
            } else if (AdiConstant.McpConstant.TRANSPORT_TYPE_STREAMABLE_HTTP.equals(mcp.getTransportType())) {
                // Reuse sseUrl/sseTimeout: streamable-HTTP also needs a POST endpoint URL + timeout
                String httpQueryString = createHttpQueryString(mcp, userMcp);
                String url = buildUrlWithQuery(mcp.getSseUrl(), httpQueryString);
                transport = new StreamableHttpMcpTransport.Builder()
                        .url(url)
                        .timeout(Duration.ofSeconds(mcp.getSseTimeout() > 0 ? mcp.getSseTimeout() : 30))
                        .logRequests(true)
                        .logResponses(true)
                        .build();
            } else {
                Map<String, String> environment = createEnvironment(mcp, userMcp);
                transport = new StdioMcpTransport.Builder()
                        .command(List.of(mcp.getStdioCommand(), mcp.getStdioArg()))
                        .environment(environment)
                        .build();
            }
            try {
                McpClient mcpClient = new DefaultMcpClient.Builder()
                        .transport(transport)
                        .build();
                result.add(new McpClientEntry(mcp, mcpClient));
            } catch (Exception e) {
                // A single MCP build failure should not abort the whole request; log and skip, continue with the rest
                log.error("Failed to build MCP client, mcpId: {}, title: {}, transportType: {}", mcp.getId(), mcp.getTitle(), mcp.getTransportType(), e);
                // On build failure the transport is not owned by any McpClient; close it to avoid connection/subprocess leaks
                try {
                    transport.close();
                } catch (Exception closeEx) {
                    log.warn("Failed to close MCP transport after build failure, mcpId: {}", mcp.getId(), closeEx);
                }
            }
        }

        return result;
    }

    public void enable(String uuid) {
        UserMcp userMcp = PrivilegeUtil.checkAndGetByUuid(uuid, this.query(), A_USER_MCP_SERVER_NOT_FOUND);
        UserMcp updateObj = new UserMcp();
        updateObj.setId(userMcp.getId());
        updateObj.setIsEnable(true);
        baseMapper.updateById(updateObj);
    }

    public void softDelete(String uuid) {
        PrivilegeUtil.checkAndDelete(uuid, this.query(), this.update(), A_USER_MCP_SERVER_NOT_FOUND);
    }

    /**
     * 加密用户的MCP设置（仅对需要加密的字段进行加密）
     * ps：目前暂时只在数据库层做加密，前后端交互时数据加解密方式待定
     *
     * @param setting mcp设置项
     * @param mcp     mcp对象
     */
    private void encryptParams(List<UserMcpCustomizedParam> setting, Mcp mcp) {
        for (UserMcpCustomizedParam userMcpCustomizedParam : setting) {
            mcp.getCustomizedParamDefinitions().stream().filter(item -> item.getName().equals(userMcpCustomizedParam.getName()) && Boolean.TRUE.equals(item.getRequireEncrypt()))
                    .findFirst()
                    .ifPresent(item -> {
                        userMcpCustomizedParam.setValue(AesUtil.encrypt(String.valueOf(userMcpCustomizedParam.getValue())));
                        userMcpCustomizedParam.setEncrypted(true);
                    });
        }
    }

    /**
     * 解密用户设置的MCP参数
     *
     * @param mcpParams 用户设置的已加密的mcp参数
     * @param mcp       mcp对象
     */
    private void decryptParams(List<UserMcpCustomizedParam> mcpParams, Mcp mcp) {
        // 用户可能从未提交过自定义参数（该 MCP 不需要参数），此时字段为 null。
        // 之前直接 for-each 会抛 NPE → 接口返回"全局异常"，等于"没有自定义参数的 MCP 无法启停"。
        // A user may never have submitted customized params (the MCP needs none), leaving this null.
        // Iterating it directly threw an NPE and the endpoint returned a generic error, which effectively
        // made every MCP without customized params impossible to toggle.
        if (null == mcpParams || mcpParams.isEmpty() || null == mcp) {
            return;
        }
        for (UserMcpCustomizedParam userMcpCustomizedParam : mcpParams) {
            mcp.getCustomizedParamDefinitions().stream()
                    .filter(item -> Boolean.TRUE.equals(userMcpCustomizedParam.getEncrypted()) && item.getName().equals(userMcpCustomizedParam.getName()) && Boolean.TRUE.equals(item.getRequireEncrypt()))
                    .findFirst()
                    .ifPresent(item -> {
                        userMcpCustomizedParam.setValue(AesUtil.decrypt(String.valueOf(userMcpCustomizedParam.getValue())));
                        userMcpCustomizedParam.setEncrypted(false);
                    });
        }
    }

    public void setMcpInfo(UserMcpDto dto, Mcp mcp) {
        if (mcp == null) {
            return;
        }
        dto.setMcpInfo(mcp);
        //解密以传到前端
        decryptParams(dto.getMcpCustomizedParams(), mcp);
    }

    /**
     * 创建MCP server中以http方式传输时所需的查询参数
     *
     * @param mcp     MCP对象
     * @param userMcp 用户MCP对象
     * @return 查询参数字符串
     */
    private String createHttpQueryString(Mcp mcp, UserMcp userMcp) {
        StringBuilder httpQueryParams = new StringBuilder();
        Map<String, String> environment = createEnvironment(mcp, userMcp);
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (value != null && !value.isEmpty()) {
                httpQueryParams.append(key).append("=").append(value).append("&");
            }
        }
        if (httpQueryParams.isEmpty()) {
            return ""; // 如果没有参数，则返回空字符串
        }
        return httpQueryParams.substring(0, httpQueryParams.length() - 1); // 去掉最后的&
    }

    /**
     * 将查询参数拼接到基础URL上：有参数时按baseUrl是否已含"?"决定用"?"或"&"连接，避免拼接出缺少分隔符的非法URL
     * Append query params to a base URL: when params exist, use "?" or "&" based on whether baseUrl already contains "?",
     * to avoid producing an invalid URL with a missing separator.
     *
     * @param baseUrl    基础URL | Base URL
     * @param queryParam 查询参数字符串，可为空 | Query string, may be empty
     * @return 拼接后的完整URL | Full URL with query params
     */
    private String buildUrlWithQuery(String baseUrl, String queryParam) {
        if (queryParam == null || queryParam.isEmpty()) {
            return baseUrl;
        }
        return baseUrl + (baseUrl.contains("?") ? "&" : "?") + queryParam;
    }


    /**
     * 创建MCP server中以stdio方式传输时所需的环境变量
     *
     * @param mcp     MCP对象
     * @param userMcp 用户MCP对象
     * @return 环境变量映射
     */
    private Map<String, String> createEnvironment(Mcp mcp, UserMcp userMcp) {
        Map<String, String> environment = new HashMap<>();
        for (McpCommonParam initParams : mcp.getPresetParams()) {
            environment.put(initParams.getName(), String.valueOf(initParams.getValue()));
        }
        for (McpCustomizedParamDefinition uninitParam : mcp.getCustomizedParamDefinitions()) {
// Uninitialized parameters defined in MCP need to use user-configured values
            // MCP中定义的未初始化参数，需要使用用户设置的值
            UserMcpCustomizedParam userParam = userMcp.getMcpCustomizedParams().stream()
                    .filter(param -> param.getName().equals(uninitParam.getName()))
                    .findFirst()
                    .orElse(null);
            if (null == userParam) {
                log.warn("No user MCP param found for uninitialized parameter: {}", uninitParam.getName());
                continue;
            }
            environment.put(uninitParam.getName(), String.valueOf(userParam.getValue()));
        }
        return environment;
    }
}
