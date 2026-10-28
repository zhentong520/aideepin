package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 短期记忆行（S2-04/ADR-003）：一行 = 一个 memory_id 的完整消息窗口。
 * <p>
 * One row = the full serialized message window for one memoryId.
 * <p>
 * 不继承 BaseEntity：记忆无 is_deleted 语义（删除即物理删），主键是业务键 memory_id
 * 而非自增 id（MapDB 模式的 key 直接平移为表主键，调用方零感知）。
 */
@Data
@TableName("chat_memory")
public class ChatMemory implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 记忆键：characterUuid（角色对话）/ chatUuid 等，与 MapDB 模式的 key 语义一致。
     */
    @TableId
    private String memoryId;

    /**
     * langchain4j ChatMessageSerializer 序列化的消息窗口 JSON。
     */
    private String messages;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
