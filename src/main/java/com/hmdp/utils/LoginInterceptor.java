package com.hmdp.utils;

import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 在前面的RefreshTokenInterceptor中已经处理了登录状态的刷新
        // 这里只需要检查用户是否登录即可
        if(UserHolder.getUser() == null) {
            // 如果用户未登录，返回401状态码
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        // 如果用户已登录，继续处理请求
        return true;
    }
}
