package com.moyz.adi.chat.controller;

import com.moyz.adi.common.entity.DbDatasource;
import com.moyz.adi.common.entity.DbSyncTask;
import com.moyz.adi.common.service.DbDatasourceService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 外部数据库数据源接口（Database MCP）：连接管理、元数据浏览、只读查询、同步到知识库。
 * 用户端只读：列表/元数据/查询/测试。数据源的新增、修改、删除收归管理后台（/admin/db-datasource）。
 * External database datasource APIs — read-only for end users (list, schema browse,
 * read-only query, connection test). Write operations live in the admin controller.
 */
@RestController
@RequestMapping("/db-datasource")
public class DbDatasourceController {

    @Resource
    private DbDatasourceService dbDatasourceService;

    @Operation(summary = "数据源列表 | Datasource list")
    @GetMapping("/list")
    public List<DbDatasource> list() {
        //平台级共享：用户端只读，返回启用中的数据源；增删改走 /admin/db-datasource
        return dbDatasourceService.listEnabled();
    }

    @Operation(summary = "测试连接(按uuid) | Test connection by uuid")
    @GetMapping("/test")
    public Map<String, Object> testByUuid(@RequestParam String uuid) {
        return dbDatasourceService.testByUuid(uuid);
    }

    @Operation(summary = "表列表 | Table list")
    @GetMapping("/tables")
    public List<Map<String, Object>> tables(@RequestParam String uuid) {
        return dbDatasourceService.listTables(uuid);
    }

    @Operation(summary = "字段列表 | Column list")
    @GetMapping("/columns")
    public List<Map<String, Object>> columns(@RequestParam String uuid, @RequestParam String table) {
        return dbDatasourceService.listColumns(uuid, table);
    }

    @Operation(summary = "只读查询 | Read-only query")
    @PostMapping("/query")
    public Map<String, Object> query(@RequestBody Map<String, Object> body) {
        String uuid = String.valueOf(body.getOrDefault("uuid", ""));
        String sql = String.valueOf(body.getOrDefault("sql", ""));
        Object limitObj = body.get("limit");
        Integer limit = null == limitObj ? null : Integer.parseInt(String.valueOf(limitObj));
        return dbDatasourceService.query(uuid, sql, limit);
    }

    @Operation(summary = "同步任务列表 | Sync task list")
    @GetMapping("/task/list")
    public List<DbSyncTask> taskList() {
        return dbDatasourceService.listTasks();
    }

    @Operation(summary = "新增或更新同步任务 | Create or update sync task")
    @PostMapping("/task/saveOrUpdate")
    public DbSyncTask saveOrUpdateTask(@RequestBody DbSyncTask task) {
        return dbDatasourceService.saveOrUpdateTask(task);
    }

    @Operation(summary = "执行同步任务(导入知识库) | Run sync task into knowledge base")
    @PostMapping("/task/run")
    public Map<String, Object> runTask(@RequestParam String uuid) {
        return dbDatasourceService.runSyncTask(uuid);
    }
}
