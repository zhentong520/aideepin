package com.moyz.adi.test.notcontroller;

/**
 * G-14 切点测试反例：不在 controller 包下的类不应被参数日志切面拦截。
 */
public class DemoPlainService {

    public void ping(String keyword) {
        //no-op
    }
}
