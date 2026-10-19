package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.cosntant.AdiConstant;
import com.moyz.adi.common.entity.DbDatasource;
import com.moyz.adi.common.entity.DbSyncTask;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.enums.SegmentModeEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.mapper.DbDatasourceMapper;
import com.moyz.adi.common.mapper.DbSyncTaskMapper;
import com.moyz.adi.common.util.SpringUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 外部数据库数据源服务（Database MCP）。
 * <p>
 * External database datasource service — connects to a user-specified database,
 * exposes schema metadata, executes read-only SQL and syncs query results into a
 * knowledge base.
 * </p>
 */
@Slf4j
@Service
public class DbDatasourceService extends ServiceImpl<DbDatasourceMapper, DbDatasource> {

    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 2000;

    /**
     * 同步任务状态。Sync task status values stored in adi_db_sync_task.last_status.
     */
    private static final String STATUS_RUNNING = "running";
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAIL = "fail";

    /**
     * 运行中的任务超过该分钟数视为僵死（进程崩溃导致），允许被重新领取。
     * A task stuck in "running" longer than this is considered dead (crashed process) and may be
     * picked up again, so a crashed run can never lock the task forever.
     */
    private static final long STALE_RUNNING_MINUTES = 30;

    /**
     * 外部数据库并发查询上限：这些连接直连客户的库，必须限速，避免把对方库打满。
     * Cap on concurrent external-database operations. These connections hit the customer's own
     * database, so they must be throttled instead of opening unlimited connections.
     */
    private static final int MAX_CONCURRENT_DB_OPS = 8;
    private final java.util.concurrent.Semaphore dbOpPermit =
            new java.util.concurrent.Semaphore(MAX_CONCURRENT_DB_OPS, true);

    @Resource
    private DbSyncTaskMapper dbSyncTaskMapper;

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    public List<DbDatasource> listMine() {
        Long userId = ThreadContext.getExistCurrentUser().getId();
        List<DbDatasource> list = list(new LambdaQueryWrapper<DbDatasource>()
                .eq(DbDatasource::getUserId, userId)
                .eq(DbDatasource::getIsDeleted, false)
                .orderByDesc(DbDatasource::getId));
        //Never expose the password to the client
        list.forEach(item -> item.setPassword(""));
        return list;
    }

    public DbDatasource getByUuid(String uuid) {
        DbDatasource ds = getOne(new LambdaQueryWrapper<DbDatasource>()
                .eq(DbDatasource::getUuid, uuid)
                .eq(DbDatasource::getIsDeleted, false));
        if (null == ds) {
            throw new BaseException(ErrorEnum.A_DATA_NOT_FOUND);
        }
        return ds;
    }

    /**
     * 取当前用户自己的数据源，防止越权读写他人数据。
     * Load a datasource owned by the current user to prevent cross-user access.
     */
    private DbDatasource getMine(String uuid) {
        DbDatasource ds = getByUuid(uuid);
        if (!ThreadContext.getExistCurrentUser().getId().equals(ds.getUserId())) {
            throw new BaseException(ErrorEnum.A_DATA_NOT_FOUND);
        }
        return ds;
    }

    private static final List<String> SUPPORTED_DB_TYPES = List.of("postgresql", "mysql");

    public DbDatasource saveOrUpdateDs(DbDatasource ds) {
        Long userId = ThreadContext.getExistCurrentUser().getId();
        if (StringUtils.isBlank(ds.getTitle())) {
            throw new BaseException(ErrorEnum.A_PARAMS_ERROR);
        }
        boolean create = StringUtils.isBlank(ds.getUuid());
        String dbType = create ? StringUtils.defaultIfBlank(ds.getDbType(), "postgresql") : ds.getDbType();
        if (StringUtils.isNotBlank(dbType)) {
            dbType = dbType.trim().toLowerCase();
            if (!SUPPORTED_DB_TYPES.contains(dbType)) {
                throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_,
                        "unsupported db type: " + dbType + ", supported: " + String.join("/", SUPPORTED_DB_TYPES));
            }
        }
        if (create) {
            //NOT NULL columns: never let a missing optional field break the insert
            ds.setDbType(dbType);
            ds.setHost(StringUtils.defaultString(ds.getHost()));
            ds.setDbName(StringUtils.defaultString(ds.getDbName()));
            ds.setUsername(StringUtils.defaultString(ds.getUsername()));
            ds.setPassword(StringUtils.defaultString(ds.getPassword()));
            ds.setJdbcParams(StringUtils.defaultString(ds.getJdbcParams()));
            ds.setRemark(StringUtils.defaultString(ds.getRemark()));
            if (null == ds.getPort() || ds.getPort() <= 0) {
                ds.setPort(defaultPort(dbType));
            }
            ds.setUuid(UUID.randomUUID().toString().replace("-", ""));
            ds.setUserId(userId);
            ds.setIsEnable(true);
            save(ds);
        } else {
            //Partial update semantics: a field left out of the payload keeps its stored value,
            //so saving a datasource from a form that only posts the title cannot blank out the
            //host / db name / user, and a missing dbType cannot silently turn mysql into postgres.
            DbDatasource exist = getMine(ds.getUuid());
            exist.setTitle(ds.getTitle());
            if (StringUtils.isNotBlank(dbType)) {
                exist.setDbType(dbType);
            }
            exist.setHost(patch(exist.getHost(), ds.getHost()));
            exist.setDbName(patch(exist.getDbName(), ds.getDbName()));
            exist.setUsername(patch(exist.getUsername(), ds.getUsername()));
            exist.setJdbcParams(patch(exist.getJdbcParams(), ds.getJdbcParams()));
            exist.setRemark(patch(exist.getRemark(), ds.getRemark()));
            if (null != ds.getPort() && ds.getPort() > 0) {
                exist.setPort(ds.getPort());
            }
            //A blank password means "keep the stored one", never wipe it
            if (StringUtils.isNotBlank(ds.getPassword())) {
                exist.setPassword(ds.getPassword());
            }
            updateById(exist);
            ds = exist;
        }
        return ds;
    }

    /**
     * 局部更新取值：入参为 null 表示“未提交”，保留原值；显式空串表示“清空”。
     * Patch semantics: null = not sent (keep stored value), "" = clear.
     */
    private String patch(String stored, String incoming) {
        if (null == incoming) {
            return StringUtils.defaultString(stored);
        }
        return incoming;
    }

    @Transactional(rollbackFor = Exception.class)
    public void softDelete(String uuid) {
        DbDatasource exist = getMine(uuid);
        exist.setIsDeleted(true);
        updateById(exist);
        //Sync tasks of a deleted datasource can never run again, drop them too
        dbSyncTaskMapper.delete(new LambdaQueryWrapper<DbSyncTask>().eq(DbSyncTask::getDatasourceUuid, uuid));
    }

    /**
     * 测试连接。
     * Test the datasource connection.
     */
    public Map<String, Object> testConnection(DbDatasource ds) {
        Map<String, Object> result = new LinkedHashMap<>();
        acquireDbPermit();
        try (Connection connection = openConnection(ds)) {
            DatabaseMetaData metaData = connection.getMetaData();
            result.put("success", true);
            result.put("product", metaData.getDatabaseProductName());
            result.put("version", metaData.getDatabaseProductVersion());
        } catch (Exception e) {
            // 连接异常的 message 常含内网 host:port、驱动内部路径等指纹，
            // 只进服务端日志，给前端的用固定文案（2026-10-15 脱敏）
            log.warn("db datasource connection failed, host:{}", ds.getHost(), e);
            result.put("success", false);
            result.put("message", "connection failed, please check host/port/username/password");
        } finally {
            dbOpPermit.release();
        }
        return result;
    }

    public Map<String, Object> testByUuid(String uuid) {
        return testConnection(getMine(uuid));
    }

    /**
     * 列举数据表。
     * List tables of the datasource.
     */
    public List<Map<String, Object>> listTables(String uuid) {
        DbDatasource ds = getMine(uuid);
        List<Map<String, Object>> tables = new ArrayList<>();
        acquireDbPermit();
        try (Connection connection = openConnection(ds)) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getTables(catalogOf(connection), schemaOf(ds), "%", new String[]{"TABLE", "VIEW"})) {
                while (rs.next()) {
                    String schema = rs.getString("TABLE_SCHEM");
                    if (isSystemSchema(schema)) {
                        continue;
                    }
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("table", rs.getString("TABLE_NAME"));
                    item.put("schema", schema);
                    item.put("remark", rs.getString("REMARKS"));
                    item.put("type", rs.getString("TABLE_TYPE"));
                    tables.add(item);
                }
            }
        } catch (Exception e) {
            log.error("list tables failed, ds:{}", uuid, e);
            throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_, e.getMessage());
        } finally {
            dbOpPermit.release();
        }
        return tables;
    }

    /**
     * 列举表字段。
     * List columns of a table.
     */
    public List<Map<String, Object>> listColumns(String uuid, String table) {
        DbDatasource ds = getMine(uuid);
        if (StringUtils.isBlank(table)) {
            throw new BaseException(ErrorEnum.A_PARAMS_ERROR);
        }
        List<Map<String, Object>> columns = new ArrayList<>();
        acquireDbPermit();
        try (Connection connection = openConnection(ds)) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getColumns(catalogOf(connection), schemaOf(ds), table, "%")) {
                while (rs.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("column", rs.getString("COLUMN_NAME"));
                    item.put("type", rs.getString("TYPE_NAME"));
                    item.put("remark", rs.getString("REMARKS"));
                    columns.add(item);
                }
            }
        } catch (Exception e) {
            log.error("list columns failed, ds:{}, table:{}", uuid, table, e);
            throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_, e.getMessage());
        } finally {
            dbOpPermit.release();
        }
        return columns;
    }

    /**
     * 只读查询（仅允许 select / with 开头的单条语句）。
     * Read-only query — only a single select/with statement is allowed.
     */
    /**
     * 只读查询（显式指定用户）。
     * <p>
     * Read-only query with an explicit user id. Workflow nodes run on @Async threads where the
     * request ThreadLocal is not available, so the caller passes the user instead of relying on
     * {@link ThreadContext}.
     * </p>
     */
    public Map<String, Object> queryForUser(String uuid, String sql, Integer limit, Long userId) {
        DbDatasource ds = getByUuid(uuid);
        if (null == userId || !userId.equals(ds.getUserId())) {
            throw new BaseException(ErrorEnum.A_DATA_NOT_FOUND);
        }
        return doQuery(ds, sql, limit);
    }

    public Map<String, Object> query(String uuid, String sql, Integer limit) {
        DbDatasource ds = getMine(uuid);
        return doQuery(ds, sql, limit);
    }

    private Map<String, Object> doQuery(DbDatasource ds, String sql, Integer limit) {
        assertReadOnly(sql);
        int maxRows = null == limit || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        acquireDbPermit();
        try (Connection connection = openConnection(ds);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setMaxRows(maxRows);
            //A hung query must never pin a connection forever
            statement.setQueryTimeout(30);
            try (ResultSet rs = statement.executeQuery()) {
                ResultSetMetaData metaData = rs.getMetaData();
                int count = metaData.getColumnCount();
                for (int i = 1; i <= count; i++) {
                    columns.add(metaData.getColumnLabel(i));
                }
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= count; i++) {
                        row.put(metaData.getColumnLabel(i), toDisplayValue(rs.getObject(i)));
                    }
                    rows.add(row);
                }
            }
        } catch (Exception e) {
            log.error("db query failed, ds:{}, sql:{}", ds.getUuid(), sql, e);
            throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_, e.getMessage());
        } finally {
            dbOpPermit.release();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("columns", columns);
        result.put("rows", rows);
        result.put("count", rows.size());
        return result;
    }

    /**
     * 取一个外部数据库操作许可，防止并发把对方库的连接打满。
     * Acquire a permit before touching an external database so concurrent requests cannot
     * exhaust the target database's connections.
     */
    private void acquireDbPermit() {
        try {
            if (!dbOpPermit.tryAcquire(30, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new BaseException(ErrorEnum.A_DB_QUERY_BUSY);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BaseException(ErrorEnum.A_DB_QUERY_BUSY);
        }
    }

    /**
     * 执行同步任务：把查询结果渲染成文本并导入知识库。
     * Run a sync task — render query rows into text and import into the knowledge base.
     */
    public Map<String, Object> runSyncTask(String taskUuid) {
        DbSyncTask task = getMineTask(taskUuid);
        Map<String, Object> result = new LinkedHashMap<>();
        //Idempotency guard: flip to RUNNING with a compare-and-set update. A second trigger while
        //the first import is still in flight updates 0 rows and is rejected instead of importing
        //the same data twice. A run stuck longer than STALE_RUNNING_MINUTES (crashed process) is
        //reclaimed so the task can never be locked forever.
        LambdaUpdateWrapper<DbSyncTask> cas = new LambdaUpdateWrapper<DbSyncTask>()
                .eq(DbSyncTask::getUuid, taskUuid)
                .eq(DbSyncTask::getIsDeleted, false)
                //Note: SQL "NULL <> 'running'" is NULL (not true), so a task that has never run
                //must be matched explicitly with isNull, otherwise it could never be claimed.
                .and(w -> w.isNull(DbSyncTask::getLastStatus)
                        .or().ne(DbSyncTask::getLastStatus, STATUS_RUNNING)
                        .or().lt(DbSyncTask::getLastSyncTime, LocalDateTime.now().minusMinutes(STALE_RUNNING_MINUTES)))
                .set(DbSyncTask::getLastStatus, STATUS_RUNNING)
                .set(DbSyncTask::getLastSyncTime, LocalDateTime.now())
                .set(DbSyncTask::getLastMessage, "running");
        if (dbSyncTaskMapper.update(null, cas) == 0) {
            log.warn("db sync task already running, task:{}", taskUuid);
            result.put("success", false);
            result.put("message", SpringUtil.getMessage(ErrorEnum.A_DB_TASK_RUNNING.getInfo()));
            return result;
        }
        try {
            Map<String, Object> queryResult = query(task.getDatasourceUuid(), task.getSqlText(), MAX_LIMIT);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rows = (List<Map<String, Object>>) queryResult.get("rows");
            if (null == rows || rows.isEmpty()) {
                task.setLastStatus(STATUS_SUCCESS);
                task.setLastRows(0);
                task.setLastMessage("no rows returned");
                task.setLastSyncTime(LocalDateTime.now());
                dbSyncTaskMapper.updateById(task);
                result.put("success", true);
                result.put("rows", 0);
                return result;
            }
            String content = renderRows(rows, task.getContentTemplate());
            String fileName = (StringUtils.isBlank(task.getTitle()) ? "db_sync" : task.getTitle()) + ".txt";
            MultipartFile file = new BytesMultipartFile("file", fileName, content);
            knowledgeBaseService.uploadDoc(task.getKbUuid(), true, file,
                    List.of(AdiConstant.DOC_INDEX_TYPE_EMBEDDING), SegmentModeEnum.TEXT, null);
            task.setLastStatus(STATUS_SUCCESS);
            task.setLastRows(rows.size());
            task.setLastMessage("imported into knowledge base");
            task.setLastSyncTime(LocalDateTime.now());
            dbSyncTaskMapper.updateById(task);
            result.put("success", true);
            result.put("rows", rows.size());
            result.put("kbUuid", task.getKbUuid());
        } catch (Exception e) {
            log.error("db sync task failed, task:{}", taskUuid, e);
            task.setLastStatus(STATUS_FAIL);
            task.setLastMessage(String.valueOf(e.getMessage()));
            task.setLastSyncTime(LocalDateTime.now());
            dbSyncTaskMapper.updateById(task);
            result.put("success", false);
            result.put("message", e.getMessage());
        } finally {
            //A crash between the CAS and the status update must never leave the task locked forever
            releaseIfStuckRunning(taskUuid);
        }
        return result;
    }

    private void releaseIfStuckRunning(String taskUuid) {
        try {
            DbSyncTask fresh = dbSyncTaskMapper.selectOne(new LambdaQueryWrapper<DbSyncTask>()
                    .eq(DbSyncTask::getUuid, taskUuid));
            if (null != fresh && STATUS_RUNNING.equals(fresh.getLastStatus())) {
                fresh.setLastStatus(STATUS_FAIL);
                fresh.setLastMessage("interrupted");
                fresh.setLastSyncTime(LocalDateTime.now());
                dbSyncTaskMapper.updateById(fresh);
            }
        } catch (Exception e) {
            log.error("release stuck sync task failed, task:{}", taskUuid, e);
        }
    }

    /**
     * 取当前用户自己的同步任务，防止越权执行他人任务。
     * Load a sync task owned by the current user to prevent cross-user access.
     */
    private DbSyncTask getMineTask(String uuid) {
        DbSyncTask task = dbSyncTaskMapper.selectOne(new LambdaQueryWrapper<DbSyncTask>()
                .eq(DbSyncTask::getUuid, uuid)
                .eq(DbSyncTask::getIsDeleted, false));
        if (null == task || !ThreadContext.getExistCurrentUser().getId().equals(task.getUserId())) {
            throw new BaseException(ErrorEnum.A_DATA_NOT_FOUND);
        }
        return task;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteTask(String uuid) {
        DbSyncTask task = getMineTask(uuid);
        task.setIsDeleted(true);
        dbSyncTaskMapper.updateById(task);
    }

    public List<DbSyncTask> listTasks() {
        Long userId = ThreadContext.getExistCurrentUser().getId();
        return dbSyncTaskMapper.selectList(new LambdaQueryWrapper<DbSyncTask>()
                .eq(DbSyncTask::getUserId, userId)
                .eq(DbSyncTask::getIsDeleted, false)
                .orderByDesc(DbSyncTask::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public DbSyncTask saveOrUpdateTask(DbSyncTask task) {
        Long userId = ThreadContext.getExistCurrentUser().getId();
        if (StringUtils.isBlank(task.getDatasourceUuid()) || StringUtils.isBlank(task.getKbUuid())
                || StringUtils.isBlank(task.getSqlText())) {
            throw new BaseException(ErrorEnum.A_PARAMS_ERROR);
        }
        //NOT NULL columns: never let a missing optional field break the insert
        task.setTitle(StringUtils.defaultString(task.getTitle()));
        task.setContentTemplate(StringUtils.defaultString(task.getContentTemplate()));
        if (null == task.getLastRows()) {
            task.setLastRows(0);
        }
        if (StringUtils.isBlank(task.getLastStatus())) {
            task.setLastStatus("");
        }
        if (StringUtils.isBlank(task.getLastMessage())) {
            task.setLastMessage("");
        }
        if (StringUtils.isBlank(task.getUuid())) {
            task.setUuid(UUID.randomUUID().toString().replace("-", ""));
            task.setUserId(userId);
            task.setIsEnable(true);
            dbSyncTaskMapper.insert(task);
        } else {
            //Never re-assign ownership: updating someone else's task must fail, not steal it
            DbSyncTask exist = getMineTask(task.getUuid());
            exist.setDatasourceUuid(task.getDatasourceUuid());
            exist.setKbUuid(task.getKbUuid());
            exist.setTitle(task.getTitle());
            exist.setSqlText(task.getSqlText());
            exist.setContentTemplate(task.getContentTemplate());
            if (null != task.getIsEnable()) {
                exist.setIsEnable(task.getIsEnable());
            }
            dbSyncTaskMapper.updateById(exist);
            task = exist;
        }
        return task;
    }

    private String renderRows(List<Map<String, Object>> rows, String template) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> row : rows) {
            if (StringUtils.isNotBlank(template)) {
                String line = template;
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    line = line.replace("{" + entry.getKey() + "}",
                            null == entry.getValue() ? "" : String.valueOf(entry.getValue()));
                }
                sb.append(line).append("\n");
            } else {
                List<String> parts = new ArrayList<>();
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    parts.add(entry.getKey() + ": " + (null == entry.getValue() ? "" : entry.getValue()));
                }
                sb.append(String.join("; ", parts)).append("\n");
            }
        }
        return sb.toString();
    }

    private void assertReadOnly(String sql) {
        if (StringUtils.isBlank(sql)) {
            throw new BaseException(ErrorEnum.A_PARAMS_ERROR);
        }
        String normalized = sql.trim().toLowerCase();
        if (!normalized.startsWith("select") && !normalized.startsWith("with")) {
            throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_, "only read-only select/with statements are allowed");
        }
        if (normalized.contains(";")) {
            throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_, "multiple statements are not allowed");
        }
        //A select can still write: "select ... into" creates a table on PostgreSQL and
        //"select ... into outfile" writes a file on MySQL. Block both, plus row locking.
        if (normalized.contains(" into ")) {
            throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_, "select-into is not allowed");
        }
        if (normalized.contains("for update") || normalized.contains("for share")
                || normalized.contains("lock in share mode")) {
            throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_, "locking reads are not allowed");
        }
    }

    private Connection openConnection(DbDatasource ds) throws SQLException {
        // 连接前的目标校验（2026-10-15）：host 与 jdbcParams 都由用户填写，
        // 是本服务里除 KbDocSource 外仅有的两个「服务器按用户指令发起出站连接」的入口
        assertConnectableHost(ds.getHost());
        assertSafeJdbcParams(ds.getJdbcParams());
        String url = buildJdbcUrl(ds);
        String driverClass = "mysql".equalsIgnoreCase(ds.getDbType()) ? "com.mysql.cj.jdbc.Driver" : "org.postgresql.Driver";
        try {
            Class.forName(driverClass);
        } catch (ClassNotFoundException e) {
            throw new SQLException("jdbc driver not found for type: " + ds.getDbType(), e);
        }
        DriverManager.setLoginTimeout(10);
        return DriverManager.getConnection(url, ds.getUsername(), ds.getPassword());
    }

    /**
     * 目标主机校验：拒绝本机回环 / 链路本地 / 云元数据地址。
     * <p>
     * <b>刻意保留私网段（10/172.16/192.168）</b>：本功能的定位就是连接企业内网数据库，
     * 禁私网等于废掉核心场景；「登录用户本就身处内网」这一边界由鉴权层保证。
     * 这里只拦「让服务器访问它自己的本机服务与云元数据端点」这类越界目标
     * （容器部署下 loopback 指向容器自身，正常业务不需要）。
     */
    private void assertConnectableHost(String host) {
        if (StringUtils.isBlank(host)) {
            throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_, "host is required");
        }
        java.net.InetAddress[] addresses;
        try {
            addresses = java.net.InetAddress.getAllByName(host.trim());
        } catch (java.net.UnknownHostException e) {
            throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_, "cannot resolve host: " + host);
        }
        for (java.net.InetAddress address : addresses) {
            boolean metadataEndpoint = "169.254.169.254".equals(address.getHostAddress());
            if (address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isAnyLocalAddress() || address.isMulticastAddress()
                    || metadataEndpoint) {
                log.warn("db datasource host rejected, host:{}, resolved:{}", host, address.getHostAddress());
                throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_, "host is not allowed: " + host);
            }
        }
    }

    /**
     * jdbcParams 高危参数黑名单。
     * <p>
     * 完全用户可控的 JDBC 参数是已知攻击面：PG 的 {@code socketFactory} 可加载任意类，
     * MySQL 的 {@code allowLoadLocalInfile}/{@code autoDeserialize} 可读服务器本地文件 /
     * 反序列化 RCE，{@code loggerFile} 可写任意路径。走黑名单而非白名单：
     * 白名单会把现有用户已配置的常规参数（sslmode、timezone 等）全部打断。
     */
    private static final java.util.Set<String> FORBIDDEN_JDBC_PARAMS = java.util.Set.of(
            "socketfactory", "socketfactoryarg", "sslfactory", "sslfactoryarg",
            "allowloadlocalinfile", "allowurlinlocalinfile", "uselocalinfile",
            "autodeserialize", "loggerfile", "loggerlevel"
    );

    private void assertSafeJdbcParams(String jdbcParams) {
        if (StringUtils.isBlank(jdbcParams)) {
            return;
        }
        // 去掉 ?/& 前缀后按 & 拆，再按 = 取参数名（大小写不敏感）
        String normalized = jdbcParams.trim();
        if (normalized.startsWith("?") || normalized.startsWith("&")) {
            normalized = normalized.substring(1);
        }
        for (String pair : normalized.split("&")) {
            String name = pair.contains("=") ? pair.substring(0, pair.indexOf('='))
                    : pair;
            name = name.trim().toLowerCase();
            if (FORBIDDEN_JDBC_PARAMS.contains(name)) {
                log.warn("db datasource jdbc param rejected: {}", name);
                throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_, "jdbc param is not allowed: " + name);
            }
        }
    }

    private String buildJdbcUrl(DbDatasource ds) {
        String params = StringUtils.isBlank(ds.getJdbcParams()) ? "" : ds.getJdbcParams();
        if (StringUtils.isNotBlank(params) && !params.startsWith("?") && !params.startsWith("&")) {
            params = "?" + params;
        }
        if ("mysql".equalsIgnoreCase(ds.getDbType())) {
            return "jdbc:mysql://" + ds.getHost() + ":" + ds.getPort() + "/" + ds.getDbName()
                    + (params.isEmpty() ? "?useSSL=false&serverTimezone=UTC&characterEncoding=utf8" : params);
        }
        return "jdbc:postgresql://" + ds.getHost() + ":" + ds.getPort() + "/" + ds.getDbName() + params;
    }

    private int defaultPort(String dbType) {
        return "mysql".equalsIgnoreCase(dbType) ? 3306 : 5432;
    }

    private String catalogOf(Connection connection) {
        try {
            return connection.getCatalog();
        } catch (SQLException e) {
            return null;
        }
    }

    /**
     * PostgreSQL uses null so that every non-system schema is listed, instead of
     * hard-coding "public" and silently hiding business tables in other schemas.
     */
    private String schemaOf(DbDatasource ds) {
        return "mysql".equalsIgnoreCase(ds.getDbType()) ? ds.getDbName() : null;
    }

    private boolean isSystemSchema(String schema) {
        if (StringUtils.isBlank(schema)) {
            return false;
        }
        String lower = schema.toLowerCase();
        return "pg_catalog".equals(lower) || "information_schema".equals(lower) || lower.startsWith("pg_");
    }

    private Object toDisplayValue(Object value) {
        if (null == value) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return value;
    }

    /**
     * 内存字节内容包装为 MultipartFile，用于复用知识库的上传流程。
     * Wrap in-memory text as a MultipartFile so the knowledge base upload flow can be reused.
     */
    public static class BytesMultipartFile implements MultipartFile {

        private final String name;
        private final String originalFilename;
        private final byte[] content;

        public BytesMultipartFile(String name, String originalFilename, String content) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.content = content.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return "text/plain";
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() throws IOException {
            return content;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(File dest) throws IOException, IllegalStateException {
            java.nio.file.Files.write(dest.toPath(), content);
        }
    }
}
