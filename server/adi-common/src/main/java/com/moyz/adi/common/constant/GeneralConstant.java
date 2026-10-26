package com.moyz.adi.common.constant;

import java.util.List;

/**
 * 通用杂项域常量（OPT-17 拆分：原 AdiConstant 顶层常量与通用嵌套类）。
 * <p>
 * {@code AdiConstant} 实现本接口，既有 {@code AdiConstant.X} 引用无需改动。
 */
public interface GeneralConstant {

    int DEFAULT_PAGE_SIZE = 10;

    /**
     * 免鉴权的静态/文档资源。
     *
     * ⚠️ 这里只允许放**以 / 开头的完整路径**，绝不能混入裸扩展名（.js / .css / .html）。
     * 原因：{@code TokenFilter#excludePath} 对本数组走 {@code endsWith} 分支，
     * 裸扩展名会让任意受保护接口「URL 末尾拼一个 .js」就整体绕过鉴权（含 /admin/ 校验）。
     * TokenFilter 侧已加 startsWith("/") 守卫做二次保险，两者必须同时成立。
     */
    String[] WEB_RESOURCES = {
            "/swagger-ui/index.html",
            "/swagger-ui",
            "/swagger-resources",
            "/v3/api-docs",
            "/favicon.ico",
            "/doc.html"
    };

    class CharacterConstant {
        private CharacterConstant() {
        }

        public static final String DEFAULT_NAME = "通用智能助手";
        public static final String DEFAULT_NAME_EN = "General AI Assistant";

        public static final int ANSWER_CONTENT_TYPE_AUTO = 1;
        public static final int ANSWER_CONTENT_TYPE_TEXT = 2;
        public static final int ANSWER_CONTENT_TYPE_AUDIO = 3;

        public static final String AUDIO_CONFIG_FIELD_ANSWER_VOICE = "answer_voice";
        public static final String AUDIO_CONFIG_FIELD_VOICE_PLATFORM = "platform";
    }

    class GenerateImage {
        private GenerateImage() {
        }

        public static final int INTERACTING_METHOD_GENERATE_IMAGE = 1;
        public static final int INTERACTING_METHOD_EDIT_IMAGE = 2;
        public static final int INTERACTING_METHOD_VARIATION = 3;
        public static final int INTERACTING_METHOD_BACKGROUND_GENERATION = 4;

        public static final int STATUS_DOING = 1;
        public static final int STATUS_FAIL = 2;
        public static final int STATUS_SUCCESS = 3;
    }

    int AI_IMAGE_TYPE_REGULAR = 1;
    int AI_IMAGE_TYPE_THUMBNAIL = 2;
    int AI_IMAGE_TYPE_REGULAR_MARK = 3;
    int AI_IMAGE_TYPE_THUMBNAIL_MARK = 4;

    String DRAW_TYPE_PUBLIC = "public";
    String DRAW_TYPE_STARRED = "starred";
    String DRAW_TYPE_MINE = "mine";

    String MP_LIMIT_1 = "limit 1";

    /**
     * 文件存储在本地
     */
    int STORAGE_LOCATION_VALUE_LOCAL = 1;

    /**
     * 文件存储到阿里云OSS
     */
    int STORAGE_LOCATION_VALUE_ALI_OSS = 2;

    String URL_PREFIX_FILE = "/file/";
    String URL_PREFIX_IMAGE = "/image/";
    String URL_PREFIX_MY_IMAGE = "/my-image/";
    String URL_PREFIX_MY_THUMBNAIL = "/my-thumbnail/";

    List<String> IMAGE_EXTENSIONS = List.of("jpg", "jpeg", "png", "gif", "bmp", "webp");

    String COLUMN_NAME_IS_DELETE = "is_deleted";
    String COLUMN_NAME_USER_ID = "user_id";
    String COLUMN_NAME_ID = "id";
    String COLUMN_NAME_UUID = "uuid";

    String FORM_DATA_BOUNDARY_PRE = "----WebKitFormBoundary";
}
