# aideepin 备份策略（S1-11）

生产此前**零备份**。本目录提供数据库 + 数据面的定时备份与恢复工具，随生产 compose 以
`aideepin-backup` sidecar 常驻。

## 备份什么

| 对象 | 方式 | 保留 |
|---|---|---|
| Postgres 数据库 | `pg_dump -Fc` 全量（含 pgvector/AGE 数据） | 每日 7 份 + 每周 4 份 |
| `/data/aideepin` 数据面 | tar.gz 整目录（files/images/thumbnails/chat-memory/ 等） | 同上，与 DB 同目录存放 |
| chat-memory.db | 包含在数据面 tar 内（S2-04 迁 Postgres 前的临时备份面） | 同上 |

## 什么时候跑

cron（`crontab` 文件，随 sidecar 挂载）：

- 每日 02:30 → `backup.sh daily`
- 每周日 03:30 → `backup.sh weekly`

时区跟随 `TZ`（.env），默认避开业务高峰。

## 备份落在哪

`/data/aideepin-backups/`（宿主机），容器内挂载为 `/backups`：

```
/backups/daily/2026-10-27/db.dump + data.tar.gz
/backups/weekly/2026-10-19/db.dump + data.tar.gz
```

**⚠️ `/data/aideepin-backups` 与数据面、数据库仍在同一台机器上。** 必须再做一层
异地/异盘同步（rclone/rsync 到对象存储或另一台机器），否则机器级故障仍会丢全部数据。
建议在宿主机 crontab 追加一条 rsync/rclone 即可（本仓库不绑定具体远端）。

## 手动备份 / 恢复

```sh
# 手动触发一次日备份
docker exec aideepin-backup /usr/local/bin/backup.sh daily

# 恢复（先停 api：docker stop aideepin-api，恢复完再起）
docker exec aideepin-backup /usr/local/bin/restore.sh /backups/daily/2026-10-27
# 只恢复数据库 / 只恢复数据面
docker exec aideepin-backup /usr/local/bin/restore.sh /backups/daily/2026-10-27 only-db
docker exec aideepin-backup /usr/local/bin/restore.sh /backups/daily/2026-10-27 only-data
```

## 恢复演练步骤（验收要求：空库完整恢复成功一次）

以下步骤已在开发环境（dev-compose 的 PG16）真实演练通过，生产演练按同样顺序执行：

1. **备份**：`docker exec aideepin-backup /usr/local/bin/backup.sh daily`
2. **建空库**：`docker exec aideepin-dev-postgres psql -U aideepin -c "CREATE DATABASE restore_test;"`
3. **恢复数据库**（容器内指向空库）：
   ```sh
   ADI_DB_NAME=restore_test /usr/local/bin/restore.sh <backup_dir> only-db
   ```
4. **校验**：
   - 表数量一致：`psql -c "select count(*) from pg_tables where schemaname='public';"`（与生产库对比）
   - 关键表行数一致：`adi_user` / `adi_conversation` / `workflow_run` 等
   - Flyway 校验通过：应用连恢复库能正常启动（`flyway_schema_history` 完整）
5. **恢复数据面**：解包 data.tar.gz 到临时目录，抽查 `files/` 图片可读、`chat-memory/` 存在
6. **清理**：`DROP DATABASE restore_test;`

## 已知边界

- MapDB 的 `chat-memory.db` 是运行中文件，tar 拷贝存在理论上的不一致窗口；
  备份安排在凌晨低峰，且 S2-04 会把 ChatMemory 外置 Postgres，此边界随之消失。
- `pg_dump -Fc` 与 `pg_restore` 由 postgres:16-alpine 提供，要求 Postgres 服务端
  大版本 ≤16（生产库为 PG16，匹配）。
- 备份未加密：备份目录包含全部业务数据，异地同步链路请自行启用 TLS / 服务端加密。
