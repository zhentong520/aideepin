package com.moyz.adi.test.controller;

/**
 * G-14 切点测试用的假 Controller（真实包路径 com.moyz.adi.*.controller 的替身）。
 * 静态内部类 DTO 模拟 LoginReq：敏感信息在对象字段里。
 */
public class DemoLoginController {

    public static class DemoLoginReq {
        private final String email = "alice@example.com";
        private final String password = "PlaintextPass!42";

        public String getEmail() {
            return email;
        }

        public String getPassword() {
            return password;
        }
    }

    public void login(DemoLoginReq req) {
        //no-op, only the intercepted invocation matters
    }

    public void ping(String keyword) {
        //no-op
    }
}
