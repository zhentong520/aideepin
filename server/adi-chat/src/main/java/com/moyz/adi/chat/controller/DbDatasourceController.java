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
 * External database datasource APIs — connection management, schema browse,
 * read-only query and knowledge base sync.
 */
@RestController
@RequestMapping("/db-datasource")
public class DbDatasourceController {

    @Resource
    private DbDatasourceService dbDatasourceService;

    @Operation(summary = "数据源列表 | Datasource list")
    @GetMapping("/list")
    public List<DbDatasource> list() {
        return dbDatasourceService.listMine();
    }

    @Operation(summary = "新增或更新数据源 | Create or update datasource")
    @PostMapping("/saveOrUpdate")
    public DbDatasource saveOrUpdate(@RequestBody DbDatasource datasource) {
        return dbDatasourceService.saveOrUpdateDs(datasource);
    }

    @Operation(summary = "删除数据源 | Delete datasource")
    @PostMapping("/delete")
    public Boolean delete(@RequestParam String uuid) {
        dbDatasourceService.softDelete(uuid);
        return true;
    }

    @Operation(summary = "测试连接(按配置) | Test connection by config")
    @PostMapping("/test")
    public Map<String, Object> test(@RequestBody DbDatasource datasource) {
        return dbDatasourceService.testConnection(datasource);
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
