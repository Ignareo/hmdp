package com.hmdp.dto;

import lombok.Data;

// @Data：Lombok 会在编译时自动为所有字段生成 getter、setter、toString、equals、hashCode 等方法（不需要显式写）
@Data
public class LoginFormDTO {
    private String phone;
    private String code;
    private String password;
}
