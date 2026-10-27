package com.moyz.adi.common.base;

import com.moyz.adi.common.service.KbDocSourceService;
import com.moyz.adi.common.service.KnowledgeBaseService;
import com.moyz.adi.common.service.ModelHealthService;
import com.moyz.adi.common.service.SysConfigService;
import com.moyz.adi.common.service.embedding.EmbeddingOrphanSweepService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Jobs {

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Resource
    private SysConfigService sysConfigService;

    @Resource
    private ModelHealthService modelHealthService;

    @Resource
    private KbDocSourceService kbDocSourceService;

    @Resource
    private EmbeddingOrphanSweepService embeddingOrphanSweepService;

    @Scheduled(initialDelay = 180_000, fixedDelay = 10 * 60_000)
    @SchedulerLock(name = "Jobs_scheduledHealthCheck", lockAtMostFor = "10m")
    public void scheduledHealthCheck() {
        log.info("Scheduled health check starting");
        modelHealthService.checkAll();
    }

    @Scheduled(initialDelay = 10 * 60 * 1000, fixedDelay = 10 * 60 * 1000)
    @SchedulerLock(name = "Jobs_reloadConfig", lockAtMostFor = "5m")
    public void reloadConfig() {
        sysConfigService.loadAndCache();
    }

    @Scheduled(fixedDelay = 60 * 1000)
    @SchedulerLock(name = "Jobs_updateKbStatistic", lockAtMostFor = "10m")
    public void run() {
        knowledgeBaseService.updateStatistic();
    }

    /**
     * 到期的 URL / API 数据源重新抓取。10 分钟一轮，与最短同步周期（10 分钟）对齐：
     * 更密的扫描没有意义，更慢则赶不上用户配的周期。
     */
    @Scheduled(initialDelay = 120_000, fixedDelay = 10 * 60_000)
    @SchedulerLock(name = "Jobs_syncDocSources", lockAtMostFor = "30m")
    public void syncDocSources() {
        int refreshed = kbDocSourceService.syncDueSources();
        if (refreshed > 0) {
            log.info("Scheduled document source sync done, refreshed:{}", refreshed);
        }
    }

    /**
     * S1-15：每日凌晨 03:30 孤儿向量对账。删除路径的向量清理是尽力而为，残留向量
     * 不可达但占存储；每天一轮分批收敛，量级为零成本（无孤儿时一条查询都不执行删除）。
     */
    @Scheduled(cron = "0 30 3 * * ?")
    @SchedulerLock(name = "Jobs_sweepOrphanEmbeddings", lockAtMostFor = "1h")
    public void sweepOrphanEmbeddings() {
        int deleted = embeddingOrphanSweepService.sweep();
        if (deleted > 0) {
            log.info("Scheduled orphan embedding sweep done, deleted:{}", deleted);
        }
    }
}
