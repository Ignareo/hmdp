package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id){
        // 1.从redis根据id查数据
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+id);
        // 2.判断redis中是否存在(null、空字符串、只包含空白字符)
        if(StrUtil.isNotBlank(shopJson)){
            // 将字符串 shopJson（通常是 JSON 格式的数据）反序列化为 Shop 类型的 Java 对象
            Shop shop = JSONUtil.toBean(shopJson,Shop.class);
            return Result.ok(shop);
        }
        // 4.若redis无，则从数据库中根据id查数据
        Shop shop = getById(id);
        // 4.1 若DB不存在shop
        if(shop == null){
            // 缓存空对象 ⚠️
            stringRedisTemplate.opsForValue().set(
                    CACHE_SHOP_KEY+id,
                    "",
                    CACHE_NULL_TTL,
                    TimeUnit.MINUTES);
            return Result.fail("店铺不存在");
        }
        // 5.将数据写入redis中
        stringRedisTemplate.opsForValue().set(
                CACHE_SHOP_KEY+id,
                // 将 shop 这个 Java 对象序列化为 JSON 字符串
                JSONUtil.toJsonStr(shop),
                CACHE_SHOP_TTL,
                TimeUnit.MINUTES
        );
        // 6.返回数据
        return Result.ok(shop);
    }

    @Override
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id == null){
            return Result.fail("店铺id不能为空");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
