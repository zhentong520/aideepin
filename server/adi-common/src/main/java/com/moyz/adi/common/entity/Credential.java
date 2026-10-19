package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 二开新增（T2-07，2026-10-19）：凭据库。
 * 只存 AES-256-GCM 密文 + 随机 IV + 主密钥版本；主密钥仅从环境变量 ADI_CRED_KEY 读取，不入库不入日志。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("adi_credential")
@Schema(title = "凭据库 | credential vault")
public class Credential extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableField("uuid")
    private String uuid;

    @TableField("type")
    private String type;

    @TableField("cipher_text")
    private String cipherText;

    @TableField("iv")
    private String iv;

    @TableField("key_version")
    private Integer keyVersion;
}
