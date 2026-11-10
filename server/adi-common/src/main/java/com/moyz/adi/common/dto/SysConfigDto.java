package com.moyz.adi.common.dto;

import lombok.Data;

@Data
public class SysConfigDto {
    /** 主键（del/{id} 接口依赖；MPPageUtil 同名自动拷贝） */
    private Long id;
    private String name;
    private String value;
}
