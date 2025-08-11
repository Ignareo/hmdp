package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.properties.JwtProperties;
import com.hmdp.service.IUserService;
import com.hmdp.utils.JwtClaimsConstant;
import com.hmdp.utils.JwtUtil;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.JwtClaimsConstant.USER_ID;
import static com.hmdp.utils.JwtUtil.createJwt;
import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
// Lombok 提供的注解，在类中自动生成一个名为 log 的日志记录器对象（Logger）
// 就可以直接使用 log.debug()、log.info() 等方法进行日志输出，而不用手动声明 Logger 变量
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private JwtProperties jwtProperties;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. 验证手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("Phone number is valid");
        }
        // 2. 生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 3. 保存验证码到redis 😋
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 4. 发送验证码
        log.debug("I have sent the coode: {}", code);
        // 5. 返回验证码
        return Result.ok(code);
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1. 获取手机号
        String phone = loginForm.getPhone();
        // 2. 验证格式
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("Phone number is valid");
        }
        // 3. 获取验证码
        String code = loginForm.getCode();
        // 3. 从redis中获取验证码 😋
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (cacheCode == null) {
            return Result.fail("验证码已过期，请重新获取");
        }
        if (!cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }
        // 4. 校验通过后，删除验证码
        stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);
        // 5. 根据手机号查询用户 ⚠️
        User user = query().eq("phone", phone).one();
        // 6. 不存在则创建
        if(user == null){
            log.debug("Doesn't exist user with phone: {}, create user", phone);
            user = createUserWithPhone(phone);
        }
        // 7. 创建jwt令牌
        Map<String,Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID,user.getId());
        String jwttoken = JwtUtil.createJwt(
                jwtProperties.getUserSecretKey(),
                jwtProperties.getUserTtl(),
                claims
        );

        // 8.将User对象转为HashMap存储
        // 8.1.将User对象转为UserDTO对象
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 8.2.将UserDTO对象转为Map对象
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        // 9. 将用户信息存入Redis （key为LOGIN_USER_KEY + userId，value为userMap）
        String tokenKey = LOGIN_USER_KEY + userDTO.getId();
        userMap.put("jwttoken",jwttoken);
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);

        // 10. 设置过期时间
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.SECONDS);

        // 11. 返回token
        return Result.ok(jwttoken);
    }

    private User createUserWithPhone(String phone) {
        // 1. 创建用户对象
        User user = new User();
        // 2. 设置手机号、昵称
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(5));
        // 3. 保存到数据库
        save(user);
        return user;
    }
}
