package com.lianshang.cloud.client.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

import com.lianshang.cloud.client.config.SeriesLogsConfigRegistrar;

/**
 * 开启连续日志（可跨平台）功能
 * 
 * 1.同一一次请求，在平台的所有日志中统一加前缀“【前缀变量值】”
 * 2.跨平台访问，只要加参数传递过去，日志可以跨平台保持一致性
 * 3.参数传递形式，可以用header，cookie，或者请求地址追加参数
 * 4.value值是用户自定义的参数名称，默认lsReq
 * 5.日志配置的pattern格式中，加 %X{此处的参数值(value值)}，如果用户不自定义日志，系统默认配置的日志已经追加了日志标志符。
 * 6.从参数中可以获取日志前缀值，本地不会生成，否则随机生成一个日志前缀值
 * @author 孙龙云
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(SeriesLogsConfigRegistrar.class)
public @interface EnableSeriesLogs {
	/**
	 * 连续注解的参数名称，可以是header中的字段，可以cookie，可以是参数。
	 * 默认值 lsReq
	 * 例如：url?lsReq=123456。那么系统中一次request请求中的日志都会自动添加【123456】
	 * @return
	 */
	public String value() default "lsReq";
}
