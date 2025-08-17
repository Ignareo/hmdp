package com.hmdp;

import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.annotation.Resource;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
//@ActiveProfiles("test")  // 使用test配置
public class RedisIdWorkerImprovedTest {

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private ExecutorService es;

    @BeforeEach
    public void setUp() {
        es = Executors.newFixedThreadPool(100); // 减少线程数，避免资源过度消耗
    }

    @Test
    public void testRedisConnection() {
        System.out.println("=== 测试Redis连接 ===");
        try {
            // 测试Redis连接
            String testKey = "test:connection";
            stringRedisTemplate.opsForValue().set(testKey, "test");
            String result = stringRedisTemplate.opsForValue().get(testKey);
            stringRedisTemplate.delete(testKey);

            Assertions.assertEquals("test", result, "Redis连接测试失败");
            System.out.println("✓ Redis连接正常");
        } catch (Exception e) {
            System.err.println("✗ Redis连接失败: " + e.getMessage());
            Assertions.fail("Redis连接失败，请检查Redis服务是否启动: " + e.getMessage());
        }
    }

    @Test
    public void testSingleIdGeneration() {
        System.out.println("=== 测试单个ID生成 ===");
        try {
            long id = redisIdWorker.nextId("test");
            System.out.println("生成的ID: " + id);
            Assertions.assertTrue(id > 0, "生成的ID应该大于0");
            System.out.println("✓ 单个ID生成成功");
        } catch (Exception e) {
            System.err.println("✗ ID生成失败: " + e.getMessage());
            throw e;
        }
    }

    @Test
    public void testConcurrentIdGeneration() throws InterruptedException {
        System.out.println("=== 测试并发ID生成 ===");

        // 减少测试规模以便调试
        int threadCount = 50;
        int idsPerThread = 20;
        int expectedTotal = threadCount * idsPerThread;

        Set<Long> idSet = ConcurrentHashMap.newKeySet();
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        System.out.println("开始测试，目标生成 " + expectedTotal + " 个唯一ID...");

        Runnable task = () -> {
            String threadName = Thread.currentThread().getName();
            try {
                for (int i = 0; i < idsPerThread; i++) {
                    long id = redisIdWorker.nextId("order");
                    idSet.add(id);
                    successCount.incrementAndGet();

                    if (i == 0) { // 每个线程只打印第一个ID
                        System.out.println("线程 " + threadName + " 生成首个ID: " + id);
                    }
                }
            } catch (Exception e) {
                System.err.println("线程 " + threadName + " 发生错误: " + e.getMessage());
                errorCount.incrementAndGet();
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        };

        long begin = System.currentTimeMillis();

        // 提交任务
        for (int i = 0; i < threadCount; i++) {
            es.submit(task);
        }

        // 等待所有任务完成
        boolean finished = latch.await(30, TimeUnit.SECONDS);
        long end = System.currentTimeMillis();

        // 关闭线程池
        es.shutdown();
        if (!es.awaitTermination(5, TimeUnit.SECONDS)) {
            es.shutdownNow();
        }

        // 输出结果
        System.out.println("\n=== 测试结果 ===");
        System.out.println("测试完成状态: " + (finished ? "成功" : "超时"));
        System.out.println("耗时: " + (end - begin) + "ms");
        System.out.println("成功生成ID数量: " + successCount.get());
        System.out.println("错误数量: " + errorCount.get());
        System.out.println("去重后ID数量: " + idSet.size());
        System.out.println("预期ID数量: " + expectedTotal);

        // 检查结果
        if (!finished) {
            Assertions.fail("测试超时！可能是Redis连接问题");
        }

        if (errorCount.get() > 0) {
            Assertions.fail("生成ID过程中出现 " + errorCount.get() + " 个错误");
        }

        // 验证ID唯一性
        Assertions.assertEquals(expectedTotal, idSet.size(),
            "ID数量不正确或有重复，期望" + expectedTotal + "个，实际" + idSet.size() + "个");

        System.out.println("✓ 并发ID生成测试通过");
    }
}
