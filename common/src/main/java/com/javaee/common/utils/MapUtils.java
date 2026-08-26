package com.javaee.common.utils;

import java.util.HashMap;
import java.util.Map;

/**
 * Map 通用转换工具。
 */
public final class MapUtils {

    private MapUtils() {
    }

    /**
     * 把任意 key 类型的 Map 转为字符串 key 的 Map（跳过 null key）。
     */
    public static Map<String, Object> toStringObjectMap(Map<?, ?> map) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                result.put(entry.getKey().toString(), entry.getValue());
            }
        }
        return result;
    }
}
