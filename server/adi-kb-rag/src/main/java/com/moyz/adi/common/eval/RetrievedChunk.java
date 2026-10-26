package com.moyz.adi.common.eval;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 送进评分器的一个检索块：正文 + 所属文档标题（出处抬头，来自 citation 元数据）。
 * docTitle 允许为空（检索链路未带抬头时），此时「命中 gold 来源」只能靠正文覆盖判定。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetrievedChunk {

    private String text;

    private String docTitle;
}
