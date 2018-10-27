package com.lianshang.cloud.client.config;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.slf4j.MDC;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.lianshang.cloud.client.annotation.LsCloudAutowired;
import com.lianshang.cloud.client.utils.CRC8Util;
import com.lianshang.cloud.client.utils.JsonUtils;

import lombok.extern.slf4j.Slf4j;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

/**
 * 服务消费者配置管理
 */
@Slf4j
@EnableEurekaClient
@EnableDiscoveryClient
@ConditionalOnMissingBean(ClientStartConfig.class)
public class ClientStartConfig implements ApplicationContextAware, BeanPostProcessor, WebMvcConfigurer {

	public static final String CLOUD_CLIENT_TEMPLATE = "serverNameRestTemplate";
	public static final String URL_CLIENT_TEMPLATE = "urlNameRestTemplate";
	/**
	 * 根据服务名查询的restTemplate
	 */
	private static RestTemplate serverNameRestTemplate;
	/**
	 * 根据url查询的restTemplate
	 */
	private static RestTemplate urlNameRestTemplate;

	/**
	 * 创建 RestTemplate
	 */
	@Bean("serverNameRestTemplate")
	@LoadBalanced
	@Order(Integer.MAX_VALUE)
	RestTemplate serviceNameTemplate() {
		return new RestTemplate();
	}

	/**
	 * 创建 RestTemplate
	 */
	@Bean("urlNameRestTemplate")
	@Order(Integer.MAX_VALUE)
	RestTemplate urlNameRestTemplate() {
		return new RestTemplate();
	}

	@Nullable
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}

	@Nullable
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		setFieldIfNecessary(bean);
		return bean;
	}

	/**
	 * 给bean设置字段,如果有必要
	 * 
	 * @param targetBean
	 */
	private void setFieldIfNecessary(Object targetBean) {
		Field[] fields = targetBean.getClass().getDeclaredFields();
		for (Field f : fields) {
			LsCloudAutowired lsCloudAutowired = f.getAnnotation(LsCloudAutowired.class);
			if (null != lsCloudAutowired) {
				f.setAccessible(true);
				String serviceName = lsCloudAutowired.value();
				boolean isDirect = lsCloudAutowired.direct();
				Object target = LsCloudServiceProxy.getProxy(f.getType(), serviceName, isDirect);
				try {
					f.set(targetBean, target);
				} catch (Exception e) {
					e.printStackTrace();
				}
				f.setAccessible(false);
			}
		}
	}

	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		// 获取 restTemplate
		serverNameRestTemplate = (RestTemplate) applicationContext.getBean(CLOUD_CLIENT_TEMPLATE);
		urlNameRestTemplate = (RestTemplate) applicationContext.getBean(URL_CLIENT_TEMPLATE);
	}

	/**
	 * 对controller 的添加LsCloudAutowired 注解的字段 添加代理
	 */
	private static class LsCloudServiceProxy implements MethodInterceptor {
		// 接口全路径名称
		private String interfaceName;
		// 服务名或者ip地址
		private String serviceName;
		// 是否直连
		private boolean isDirect;

		LsCloudServiceProxy(String serviceName, String interfaceName, boolean isDirect) {
			this.serviceName = serviceName;
			this.interfaceName = interfaceName;
			this.isDirect = isDirect;
		}

		public static Object getProxy(Class clzz, String serviceName, boolean isDirect) {
			Enhancer enhancer = new Enhancer();
			if (clzz.isInterface()) {
				enhancer.setInterfaces(new Class[] { clzz });
			} else {
				enhancer.setSuperclass(clzz);
			}
			enhancer.setCallback(new LsCloudServiceProxy(serviceName, clzz.getName(), isDirect));
			return enhancer.create();
		}

		public Object intercept(Object object, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {

			try {
				String methodName = method.getName();
				if (methodName.equals("toString")) {
					return interfaceName;
				}
				String url = getUrl();

				RestTemplate restTemplate = getRestTemplate();

				Map<String, Object> postParameters = new HashMap<>();
				postParameters.put("methodName", methodName);
				postParameters.put("interfaceName", interfaceName);
				postParameters.put("params", args);

				HttpHeaders headers = new HttpHeaders();
				MediaType mediaType = MediaType.parseMediaType("application/json; charset=UTF-8");
				headers.setContentType(mediaType);
				headers.add("Accept", MediaType.APPLICATION_JSON.toString());

				String paramsJson = JsonUtils.object2JsonString(postParameters);
				HttpEntity<String> formEntity = new HttpEntity<String>(paramsJson, headers);

				Object result = null;
				long start = System.currentTimeMillis();
				try {
					log.info("请求参数=>{}", paramsJson);
					log.info("请求url=>{}", url);
					log.info("lsReq==>{}", MDC.get("lsReq"));
					url = url + "?lsReq=" + MDC.get("lsReq");
					result = restTemplate.postForEntity(url, formEntity, Object.class).getBody();
				} catch (Exception e) {
					log.error("远程服务调用失败,{}", e.getMessage());
					e.printStackTrace();
					return null;
				} finally {
					log.info("响应参数:【{}】,耗时:【{}】", result, (System.currentTimeMillis() - start) + "毫秒");
				}
				return handleResult(method, result);
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}

		private RestTemplate getRestTemplate() {
			RestTemplate restTemplate = null;
			if (isDirect || isIp(serviceName)) {
				restTemplate = urlNameRestTemplate;
			} else {
				restTemplate = serverNameRestTemplate;
			}
			return restTemplate;
		}

		/**
		 * 构建url
		 *
		 * @return
		 */
		private String getUrl() {
			StringBuilder urlBuilder = new StringBuilder();
			if (!serviceName.startsWith("http")) {
				urlBuilder.append("http://");
			}
			urlBuilder.append(serviceName);
			if (!serviceName.endsWith("/")) {
				urlBuilder.append("/");
			}
			urlBuilder.append("lsCloud/execute");

			return urlBuilder.toString();
		}
	}

	/**
	 * 处理返回结果
	 *
	 * @param method
	 * @param result
	 * @return
	 */
	private static Object handleResult(Method method, Object result) {
		Type type = method.getGenericReturnType();
		String returnTypeName = type.toString();
		switch (returnTypeName) {
		case "int":
			return Integer.valueOf(result.toString());
		case "long":
			Long.valueOf(result.toString());
		case "byte":
			Byte.valueOf(result.toString());
		case "double":
			Double.valueOf(result.toString());
		case "float":
			Float.valueOf(result.toString());
		case "short":
			Short.valueOf(result.toString());
		case "char":
			Character.codePointAt(result.toString(), 0);
		case "boolean":
			return Boolean.valueOf(result.toString());

		default:
			return result;
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
				return true;
			}
			/**
			 * 获取lsReq
			 * @param request
			 * @return
			 */
			private String getLsReq(HttpServletRequest request) {
				String lsReq = request.getHeader("lsReq");
				if (StringUtils.isEmpty(lsReq)) {
					lsReq = request.getParameter("lsReq");
				}
				if(StringUtils.isEmpty(lsReq)){
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
			}

		});
	}

	/**
	 * 判断是否是ip
	 *
	 * @param serviceName
	 * @return
	 */
	private static boolean isIp(String serviceName) {
		Pattern pattern = Pattern.compile("^(http(s)?://)?(\\w+\\.?){4}(:\\w+(/)?)?$");
		return pattern.matcher(serviceName).matches();
	}
}
