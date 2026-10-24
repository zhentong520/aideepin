package com.moyz.adi.common.service.embedding;

/**
 * 当前生效的 embedding provider 描述（T7-05）。
 * <p>
 * 换 embedding 模型是知识库最典型的「静默事故」源头：向量表按「模型 + 维度」分表，
 * 切模型后新表为空、旧表成孤儿，检索不报错只是永远零命中。provider 版本就是为此
 * 存在的溯源凭据：每个知识库记录「它的向量是哪个 provider 产出的」，切换/重建/回滚
 * 全部以此为准绳，启动自检与审计日志消费同一份版本串。
 *
 * @param name       provider 名称（本地模型名或远端模型名，如 {@code bge-m3}）
 * @param version    不可变版本串（本地 {@code name@dim}，远端 {@code platform/name@dim}），
 *                   同一版本串的向量才可互相检索；它是回滚判据与审计关键字
 * @param dimension  向量维度（决定 pgvector 物理表列类型与表名后缀）
 * @param local      是否本地推理（ONNX，无外部依赖）；false = 远端 OpenAI 协议兼容端点
 * @param platform   远端平台名（本地为空）
 * @param modelName  远端模型名（本地为空）
 */
public record EmbeddingProviderInfo(String name, String version, int dimension,
                                    boolean local, String platform, String modelName) {

    /**
     * 未记录（存量知识库 / 迁移前创建）的哨兵版本：空串。
     * <p>
     * 空串必须被所有消费方当作「未知」处理——跳过比对而不是判为不一致，
     * 否则升级后第一启动就会把全部存量库刷成告警。
     */
    public static final String UNKNOWN_VERSION = "";

    public static EmbeddingProviderInfo unknown() {
        return new EmbeddingProviderInfo(UNKNOWN_VERSION, UNKNOWN_VERSION, 0, false, "", "");
    }

    public boolean isUnknown() {
        return UNKNOWN_VERSION.equals(version);
    }
}
