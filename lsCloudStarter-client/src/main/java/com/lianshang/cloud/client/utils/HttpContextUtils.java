package com.lianshang.cloud.client.utils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
/**
 * http工具类
 * @author 孙龙云
 */
public class HttpContextUtils {
	/**
	 * 获取 HttpServletRequest
	 * @return
	 */
	public static HttpServletRequest getHttpServletRequest() {
        return ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
    }

	/**
	 * 获取 HttpServletResponse
	 */
	public static HttpServletResponse getHttpServletResponse() {
		return ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getResponse();
	}

}
