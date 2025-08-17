package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 先查询秒杀券的相关信息
        // 1. 查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2.判断秒杀是否开始
        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("秒杀尚未开始！");
        }
        // 3.判断秒杀是否已经结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束！");
        }
        // 4.判断库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足！");
        }
        // 创建订单
        Long userId = UserHolder.getUser().getId();
        //创建锁对象
        SimpleRedisLock lock =  new SimpleRedisLock("order:"+userId, stringRedisTemplate);
        //获取锁
        boolean isLock =  lock.tryLock(1200);
        if(!isLock){
            //获取锁失败，返回错误或重试
            return Result.fail("不允许重复下单");
        }
        try{
            //获取代理对象（事务）
            IVoucherOrderService prox = (IVoucherOrderService) AopContext.currentProxy();
            return prox.createVoucherOrder(voucherId);
        }finally {
            //释放锁
            lock.unlock();
        }
    }

    @Override
    @Transactional
    public Result createVoucherOrder(Long voucherId){
        // 1. 获取用户id
        Long userId = UserHolder.getUser().getId();
        // 2. 判断是否购买过了
        int count = Math.toIntExact(query().eq("user_id", userId).eq("voucher_id", voucherId).count());
        if (count > 0) {
            return Result.fail("用户已经购买过一次！");
        }

        //6.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock= stock -1") // set stock = stock -1
                .eq("voucher_id", voucherId)
                .gt("stock",0)// where id = ? and stock > 0
                .update();
        if (!success) {
            return Result.fail("库存不足！");
        }

        // 7.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 订单ID
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 用户id
        voucherOrder.setUserId(userId);
        // 代金券id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        return Result.ok(orderId);
    }
}
