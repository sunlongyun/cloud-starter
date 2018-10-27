package com.lianshang.cloud.server.config;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.slf4j.MDC;
import org.springframework.beans.BeansException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.lianshang.cloud.server.annotation.LsCloudService;
import com.lianshang.cloud.server.utils.CRC8Util;

import lombok.extern.slf4j.Slf4j;

/**
 * 服务提供方,配置管理
 */
@Slf4j
@EnableEurekaClient
@ConditionalOnMissingBean(ServerStarterConfig.class)
public class ServerStarterConfig
		implements ApplicationListener<ContextRefreshedEvent>, ApplicationContextAware, WebMvcConfigurer {

	private ApplicationContext applicationContext;
	private static Map<String, Object> cloudServiceMap = new HashMap<>();

	/**
	 * 保存服务发布者的bean
	 */
	@Override
	public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {

		Map<String, Object> cloudServices = applicationContext.getBeansWithAnnotation(LsCloudService.class);
		if (!CollectionUtils.isEmpty(cloudServices)) {
			Collection<?> values = cloudServices.values();
			for (Object v : values) {
				Class<?>[] inters = v.getClass().getInterfaces();
				for (Class inter : inters) {
					if (inter.isInterface()) {
						cloudServiceMap.put(inter.getName(), v);
					}
				}
			}
		}

	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	/**
	 * 回调服务提供者
	 */
	public static Object execute(String interfaceName, String methodName, Object... params) {
		Object bean = cloudServiceMap.get(interfaceName);
		if (bean == null) {
			log.error("未找接口对应的bean==>{}", interfaceName);
			throw new RuntimeException("未找接口对应的bea【" + interfaceName + "】");
		}
		Method[] methods = bean.getClass().getMethods();
		try {
			for (Method method : methods) {
				if (method.getName().equals(methodName) && method.getParameterTypes().length == params.length) {
					Object value = method.invoke(bean, params);
					return value;
				}
			}
			throw new RuntimeException("未找到bean【" + interfaceName + "】中符合条件的方法【" + methodName + "】");
		} catch (Exception e) {
			log.error("调用service失败,{}", e);
			e.printStackTrace();
			throw new RuntimeException("调用service失败【" + e.getMessage() + "】");
		}
	}

	/**
	 * 返回api列表
	 */
	public static List<Map<String, Object>> getApiList() {
		List<Map<String, Object>> apiList = new ArrayList<>();
		Iterator<String> keys = cloudServiceMap.keySet().iterator();
		while (keys.hasNext()) {
			Map<String, Object> data = new HashMap<>();
			String key = keys.next();
			data.put("interfaceName", key);
			Object bean = cloudServiceMap.get(key);
			if (null != bean) {
				setMethodInfo(data, bean);
			}
			apiList.add(data);
		}
		return apiList;
	}

	private static void setMethodInfo(Map<String, Object> data, Object bean) {
		Class clzz = bean.getClass();
		Method[] methods = clzz.getMethods();
		if (null != methods) {
			List<Map<String, Object>> methodList = new ArrayList<>();
			data.put("methodList", methodList);
			for (Method m : methods) {
				String methodName = m.getName();
				if (methodName.equals("wait") || methodName.equals("equals") || methodName.equals("toString")
						|| methodName.equals("hashCode") || methodName.equals("getClass") || methodName.equals("notify")
						|| methodName.equals("notifyAll")) {
					continue;
				}
				Map<String, Object> methodData = new HashMap<>();
				methodList.add(methodData);

				methodData.put("methodName", methodName);
				methodData.put("args", m.getParameterTypes());
			}
		}
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new HandlerInterceptor() {
			@Override
			public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
					throws Exception {
				String lsReq = getLsReq(request);
				MDC.put("lsReq", lsReq);
				return HandlerInterceptor.super.preHandle(request, response, handler);
			}

			/**
			 * 获取lsReq
			 * 
			 * @param request
			 * @return
			 */
			private String getLsReq(HttpServletRequest request) {
				String lsReq = request.getHeader("lsReq");
				if (StringUtils.isEmpty(lsReq)) {
					lsReq = request.getParameter("lsReq");
				}
				if (StringUtils.isEmpty(lsReq)) {
					lsReq = getValueFromCookie(request, "lsReq");
				}
				if (StringUtils.isEmpty(lsReq)) {
					lsReq = "【" + UUID.randomUUID() + "_"
							+ CRC8Util.calcCrc8((System.currentTimeMillis() + "").getBytes()) + "】";
				}
				return lsReq;
			}

			/**
			 * 从cookie取值
			 * 
			 * @param request
			 * @param lsReq
			 * @return
			 */
			private String getValueFromCookie(HttpServletRequest request, String lsReq) {
				// 读取cookie
				Cookie[] cookies = request.getCookies();
				if (cookies != null) {
					// 遍历数组
					for (Cookie cookie : cookies) {
						if (cookie.getName().equals(lsReq)) {
							// 取出cookie的值
							String value = cookie.getValue();
							return value;
						}
					}
				}
				return null;
			}

			@Override
			public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
					ModelAndView modelAndView) throws Exception {
				MDC.remove("lsReq");
				HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
			}
		});
	}
}
