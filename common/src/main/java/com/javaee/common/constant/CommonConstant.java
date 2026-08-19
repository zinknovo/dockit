package com.javaee.common.constant;

/**
 * @author qxk
 * @description: 通用常量（分页/正则）
 */
public class CommonConstant {
    // 分页常量
    public static final Integer PAGE_SIZE = 10;
    public static final Integer PAGE_NUM = 1;
    public static final Integer MAX_PAGE_SIZE = 100;

    // 正则表达式常量
    public static final String EMAIL_REGEX = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
    public static final String PHONE_REGEX = "^1[3-9]\\d{9}$";
    public static final String PASSWORD_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$";

    public static final String PERIOD = ".";

    // 日期格式常量
    public static final String DATE_FORMAT = "yyyy-MM-dd";
    public static final String TIME_FORMAT = "HH:mm:ss";
    public static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";

    public static final String PATH_SEPARATOR = System.getProperty("path.separator");

    // 安全常量
    public static final String TOKEN_PREFIX = "Bearer ";
    public static final String TOKEN_CLAIM_USER_ID = "userId";
    public static final String TOKEN_CLAIM_USERNAME = "username";
    public static final String TOKEN_CLAIM_ROLE = "role";
}

