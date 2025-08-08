package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.Random;

import static com.hmdp.utils.SystemConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. 验证手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("Phone number is valid");
        }
        // 2. 生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 3. 保存验证码到session
        session.setAttribute(SESSION_CODE_KEY, code);
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
        String cacheCode = session.getAttribute(SESSION_CODE_KEY).toString();
        // 4. 校验验证码
        // ⚠️session中的code可能不存在
        if(cacheCode == null || !cacheCode.equals(code)){
            return Result.fail("Code is wrong");
        }
        // 5. 根据手机号查询用户
        // ⚠️ MP的语法
        User user = query().eq("phone", phone).one();
        // 6. 不存在则创建
        if(user == null){
            log.debug("Doesn't exist user with phone: {}, create user", phone);
            user = createUserWithPhone(phone);
        }
        // 6. 保存用户信息到session
        // ⚠️不能存整个User，只需要部分属性
        session.setAttribute(SESSION_USER_KEY, BeanUtil.copyProperties(user, UserDTO.class));
        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(5));
        return user;
    }
}
