package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * RedisIdWorker 用于���成唯一的ID
 * 该ID由时间戳和序列号组成，时间戳精确到秒，序列号每天从1开始自增
 */
@Component
public class RedisIdWorker {
    // 开始时间戳（可以任意设置）
    // 2022-01-01 00:00:00 UTC
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    // 序列号的位数
    private static final int COUNT_BITS = 32;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 生成唯一ID
     * @param keyPrefix 用于区分不同业务的前缀
     * @return 返回生成的唯一ID
     */
    public long nextId(String keyPrefix) {
        // 1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // 2.生成序列号t
        // 2.1.获取当前日期，精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.2.自增长，添加空值检查
        String key = "incr:" + keyPrefix + ":" + date;
        Long count = stringRedisTemplate.opsForValue().increment(key);

        // 检查Redis操作是否成功
        if (count == null) {
            throw new RuntimeException("Redis操作失败，无法生成ID序列号");
        }

        // 3.拼接并返回(高位是时间戳，低位是序列号)
        // 时间戳左移 COUNT_BITS 位 (此时低位都为0)，然后与 count 进行按位或操作
        return timestamp << COUNT_BITS | count;
    }
}
