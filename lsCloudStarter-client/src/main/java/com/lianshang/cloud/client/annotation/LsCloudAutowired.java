package com.lianshang.cloud.client.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
     * 知否是直连
     * @return
     */
    public boolean direct() default  false;
}
