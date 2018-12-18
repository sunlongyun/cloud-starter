package com.lianshang.cloud.client.utils;

import com.lianshang.cloud.client.beans.AA;

/**
 * 描述:
 *
 * @AUTHOR 孙龙云
 * @date 2018-12-17 下午6:50
 */
public class Test {
    public static void main(String[] args){
        Class clazz = GenericsUtils.getSuperClassGenricType(AA.class);
        System.out.println(clazz);
    }
}
