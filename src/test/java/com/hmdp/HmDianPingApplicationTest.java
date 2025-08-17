package com.hmdp;

import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
public class HmDianPingApplicationTest {
    @Resource
    private RedisIdWorker redisIdWorker;

    // 线程池，用于并发生成ID
    // 使用500个线程来模拟高并发场景
    private final ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    public void testIdWorker() throws InterruptedException {
        Set<Long> idSet = ConcurrentHashMap.newKeySet();
        CountDownLatch latch = new CountDownLatch(300);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        System.out.println("开始测试RedisIdWorker，目标生成30000个唯一ID...");

        // 每个任务是一个Runnable
        Runnable task = () -> {
            try {
                // 每个线程生成100个ID
                // 使用ConcurrentHashMap的keySet来存储ID，确保线程安全
                for (int i = 0; i < 100; i++) {
                    long id = redisIdWorker.nextId("order");
                    if (i % 20 == 0) { // 减少日志输出频率
                        System.out.println("线程 " + Thread.currentThread().getName() + " 生成ID: " + id);
                    }
                    // 将生成的ID存入Set集合中
                    idSet.add(id);
                    successCount.incrementAndGet();
                }
            } catch (Exception e) {
                System.err.println("生成ID时发生错误: " + e.getMessage());
                errorCount.incrementAndGet();
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        };

        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }

        // 等待所有任务完成，最多等待30秒
        boolean finished = latch.await(30, java.util.concurrent.TimeUnit.SECONDS);
        long end = System.currentTimeMillis();
        es.shutdown();

        if (!finished) {
            System.err.println("测试超时！部分任务未完成");
            Assertions.fail("测试超时，可能是Redis连接问题");
        }

        System.out.println("测试完成！");
        System.out.println("耗时: " + (end - begin) + "ms");
        System.out.println("成功生成ID数量: " + successCount.get());
        System.out.println("错误数量: " + errorCount.get());
        System.out.println("去重后ID数量: " + idSet.size());

        if (errorCount.get() > 0) {
            Assertions.fail("生成ID过程中出现 " + errorCount.get() + " 个错误");
        }

        Assertions.assertEquals(30000, idSet.size(), "ID数量不正确或有重复，期望30000个，实际" + idSet.size() + "个");
    }
}