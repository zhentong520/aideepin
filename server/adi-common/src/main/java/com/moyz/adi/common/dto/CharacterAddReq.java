package com.moyz.adi.common.dto;

import com.moyz.adi.common.vo.AudioConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
@Validated
public class CharacterAddReq {

    @NotBlank
    private String title;

    private String remark;

    private String aiSystemMessage;

    private List<Long> mcpIds;

    private List<Long> kbIds;

    @Schema(title = "绑定的技能id列表 | Bound agent skill id list")
    private List<Long> skillIds;

    private AudioConfig audioConfig;

    private Boolean isEnableThinking;

    private Boolean isEnableWebSearch;
}
