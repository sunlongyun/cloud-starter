package com.lianshang.cloud.client.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * direct=true或者value是ip地址,那么直接连接.
 * 否则,根据value填写的服务地址,调用springCloud的负载均衡
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LsCloudAutowired {

    /**
     * springCloud服务名称或者ip地址(含端口)
     * @return
     */
    public String value();

    /**
     * 是否是直连
     * @return
     */
    public boolean direct() default  false;
}
