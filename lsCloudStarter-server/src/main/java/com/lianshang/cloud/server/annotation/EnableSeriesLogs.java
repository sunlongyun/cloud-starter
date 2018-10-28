package com.lianshang.cloud.server.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

import com.lianshang.cloud.server.config.SeriesLogsConfigRegistrar;


/**
 * 开启连续日志（可跨平台）功能
 * 
 * 1.同一次请求，日志中统一加前缀“【前缀变量值】”
 * 2.日志记录可以跨平台保持一致性
 * 3.日志配置的pattern格式中，加 %X{lsReq}
 * @author 孙龙云
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(SeriesLogsConfigRegistrar.class)
public @interface EnableSeriesLogs {
	/**
	 * 参数名称(从参数名称获取“前缀变量值”)
	 * 参数的传递可以通过 header、cookie或者url
	 * @return
	 */
	public String value() default "lsReq";
}
