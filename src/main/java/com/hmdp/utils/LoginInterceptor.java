package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import static com.hmdp.utils.SystemConstants.SESSION_USER_KEY;

public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. ⚠️要先获取session
        HttpSession session = request.getSession();
        // 2. 从session获取用户信息
        Object userDto = session.getAttribute(SESSION_USER_KEY);
        // 3. 判断用户是否存在
        if(userDto == null){
            response.setStatus(401);
            return false;
        }
        // 4. 保存用户到ThreadLocal
        // ⚠️ 使用已经写好了的，静态的UserHolder
        UserHolder.saveUser((UserDTO) userDto);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
