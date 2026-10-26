package com.moyz.adi.common.rag;

import java.util.HashMap;
import java.util.Map;

public class EmbeddingRagContext {

    protected static final Map<String, EmbeddingRag> NAME_TO_RAG = new HashMap<>();

    private EmbeddingRagContext() {
    }

    public static EmbeddingRag get(String name) {
        return NAME_TO_RAG.get(name);
    }

    public static void add(EmbeddingRag rag) {
        NAME_TO_RAG.put(rag.getName(), rag);
    }

    /**
     * 二开新增（T1-08，2026-10-19）：测试用移除接口，避免单测注入的 mock RAG 泄漏到其他用例
     */
    public static void remove(String name) {
        NAME_TO_RAG.remove(name);
    }
}
