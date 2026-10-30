package com.moyz.adi.admin.controller;

import com.moyz.adi.common.annotation.AdminOnly;
import com.moyz.adi.common.entity.DbDatasource;
import com.moyz.adi.common.service.DbDatasourceService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数据源管理（管理后台）。
 * <p>
 * Datasource management — admin-only. Datasources are platform-level shared resources:
 * end users may only use them (Database node / knowledge base sync), while create / update /
 * delete / enable are done here. user_id is kept for compatibility but no longer carries
 * ownership semantics (platform rows are stored with user_id = 0).
 * </p>
 */
@RestController
@RequestMapping("/admin/db-datasource")
@AdminOnly
public class AdminDbDatasourceController {

    @Resource
    private DbDatasourceService dbDatasourceService;

    @Operation(summary = "数据源列表（含停用） | Datasource list (including disabled)")
    @GetMapping("/list")
    public List<DbDatasource> list() {
        return dbDatasourceService.listAllForAdmin();
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
        return dbDatasourceService.testByUuidForAdmin(uuid);
    }

    @Operation(summary = "表列表 | Table list")
    @GetMapping("/tables")
    public List<Map<String, Object>> tables(@RequestParam String uuid) {
        return dbDatasourceService.listTablesForAdmin(uuid);
    }

    @Operation(summary = "字段列表 | Column list")
    @GetMapping("/columns")
    public List<Map<String, Object>> columns(@RequestParam String uuid, @RequestParam String table) {
        return dbDatasourceService.listColumnsForAdmin(uuid, table);
    }
}
