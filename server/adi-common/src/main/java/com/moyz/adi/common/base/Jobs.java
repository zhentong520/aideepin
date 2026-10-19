package com.moyz.adi.common.base;

import com.moyz.adi.common.service.KbDocSourceService;
import com.moyz.adi.common.service.KnowledgeBaseService;
import com.moyz.adi.common.service.ModelHealthService;
import com.moyz.adi.common.service.SysConfigService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
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

    @Scheduled(initialDelay = 180_000, fixedDelay = 10 * 60_000)
    public void scheduledHealthCheck() {
        log.info("Scheduled health check starting");
        modelHealthService.checkAll();
    }

    @Scheduled(initialDelay = 10 * 60 * 1000, fixedDelay = 10 * 60 * 1000)
    public void reloadConfig() {
        sysConfigService.loadAndCache();
    }

    @Scheduled(fixedDelay = 60 * 1000)
    public void run() {
        knowledgeBaseService.updateStatistic();
    }

    /**
     * 到期的 URL / API 数据源重新抓取。10 分钟一轮，与最短同步周期（10 分钟）对齐：
     * 更密的扫描没有意义，更慢则赶不上用户配的周期。
     */
    @Scheduled(initialDelay = 120_000, fixedDelay = 10 * 60_000)
    public void syncDocSources() {
        int refreshed = kbDocSourceService.syncDueSources();
        if (refreshed > 0) {
            log.info("Scheduled document source sync done, refreshed:{}", refreshed);
        }
    }
}
