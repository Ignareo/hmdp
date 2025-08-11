package com.hmdp.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;


public class JwtUtil {
    /**
     * 生成jwt
     * 使用Hs256算法, 私匙使用固定秘钥
     *
     * @param secretKey 秘钥
     * @param ttlMillis 过期时间(毫秒)
     * @param claims    设置的信息
     */
    public static String createJwt(String secretKey, long ttlMillis, Map<String, Object> claims) {
        // 设定签名算法（HS256）
        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;

        // 生成过期时间
        long expMillis = System.currentTimeMillis() + ttlMillis;
        Date exp = new Date(expMillis);

        // 构建JWT
        JwtBuilder builder = Jwts.builder()
                // 设置 claim
                .setClaims(claims)
                // 设置 secretyKey和签名算法
                .signWith(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8)), signatureAlgorithm)
                // 设置过期时间
                .setExpiration(exp);

        return builder.compact();
    }

    /**
     * Token解密
     *
     * @param secretKey jwt秘钥
     * @param token     加密后的token
     */
    public static Claims parseJwt(String secretKey, String token) {
        // 得到DefaultJwtParser
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey.getBytes(StandardCharsets.UTF_8))
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims;
    }
}