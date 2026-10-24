package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/**
 * T9-01/05/06 单测支撑：纯 mock 单测里 MyBatis-Plus 未启动，lambda 链式
 * （ChainWrappers.lambdaQueryChain(...).eq(Entity::getX, ...)）解析列名需要
 * TableInfo 缓存，这里手动初始化（幂等，已注册时跳过）。
 */
public final class MpTableInfoTestSupport {

    private MpTableInfoTestSupport() {
    }

    public static void initTableInfo(Class<?>... entityClasses) {
        for (Class<?> entityClass : entityClasses) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, entityClass);
        }
    }
}
