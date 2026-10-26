package com.moyz.adi.common.config.props;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 文本清洗配置。
 * <p>
 * Text cleanup applied to parsed document text before chunking.
 * <p>
 * OPT-17 拆分：原 {@code AdiProperties.Clean} 嵌套类提为顶层类，Spring 绑定键不变。
 */
@Data
public class Clean {
    /**
     * 清洗总开关。关闭时解析原文直接入库（与改造前行为一致）。
     * <p>
     * Master switch; when off the raw parsed text is stored as-is.
     */
    private boolean enabled = true;

    /**
     * 合并「软换行」：PDF/网页抽取常把一段中文拆成多行，行尾没有句末标点时把它接回上一行。
     * 对表格、诗歌、代码块会有副作用，默认关闭。
     * <p>
     * Merge soft line breaks introduced by PDF/web extraction. Off by default because it
     * mangles tables, verse and code blocks.
     */
    private boolean mergeSoftLineBreaks = false;

    /**
     * 连续合并软换行的上限，防止整篇无标点文本被并成一块。
     * <p>
     * Max consecutive soft line breaks merged into one paragraph.
     */
    private int maxConsecutiveMerge = 20;

    /**
     * 丢弃重复短行（页眉页脚）：同一行在全文出现次数达到阈值且长度很短时删除。
     * 正文里合法重复（如「注意事项」）会被误删，默认关闭。
     * <p>
     * Drop repeated short lines (headers/footers). Off by default: legitimate repetition in
     * body text would be removed too.
     */
    private boolean dropDuplicateLines = false;

    /**
     * 判定为页眉页脚的最小出现次数 | Min occurrences before a short line counts as a header/footer.
     */
    @Min(value = 2, message = "adi.ingest.clean.duplicate-line-min-occurrences 至少 2（1 会误杀全部行）")
    private int duplicateLineMinOccurrences = 5;

    /**
     * 判定为页眉页脚的最大行长度 | Max line length (chars) still considered a header/footer.
     */
    private int duplicateLineMaxLength = 60;
}
