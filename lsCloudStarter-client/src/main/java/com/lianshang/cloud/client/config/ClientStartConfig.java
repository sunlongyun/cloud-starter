package com.lianshang.cloud.client.config;

import com.lianshang.cloud.client.annotation.LsCloudAutowired;
import com.lianshang.cloud.client.beans.LsCloudResponse;
import com.lianshang.cloud.client.enums.ResponseCodeEnum;
import com.lianshang.cloud.client.utils.GenericsUtils;
import com.lianshang.cloud.client.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import org.apache.commons.lang.StringUtils;
import org.slf4j.MDC;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 服务消费者配置管理
 */
@Slf4j
@EnableEurekaClient
@EnableDiscoveryClient
@ConditionalOnMissingBean(ClientStartConfig.class)
@EnableFeignClients("com")
@EnableCircuitBreaker
@Order(1000)
public class ClientStartConfig implements ApplicationContextAware, BeanPostProcessor {

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
	@Bean(CLOUD_CLIENT_TEMPLATE)
	@LoadBalanced
	@ConditionalOnMissingBean(name = CLOUD_CLIENT_TEMPLATE)
	RestTemplate serviceNameTemplate() {
		return new RestTemplate();
	}

	ClientStartConfig() {
		log.info("ClientStartConfig-------");
	}
	/**
	 * 创建 RestTemplate
	 */
	@Bean(URL_CLIENT_TEMPLATE)
	@ConditionalOnMissingBean(name = URL_CLIENT_TEMPLATE)
	RestTemplate urlNameRestTemplate() {
		return new RestTemplate();
	}

	@Nullable
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		setFieldIfNecessary(bean);
		return bean;
	}

	@Nullable
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		//setFieldIfNecessary(bean);
		return bean;
	}

	/**
	 * 给bean设置字段,如果有必要
	 *
	 * @param targetBean
	 */
	private void setFieldIfNecessary(Object targetBean) {
		Class targetClzz = targetBean.getClass();
		if (isProxyClass(targetClzz)
		|| targetClzz.getName().contains("@")) {
			targetClzz = targetClzz.getSuperclass();
		}
		Field[] fields = targetClzz.getDeclaredFields();
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

			RestTemplate restTemplate = getRestTemplate();

			//泛型类型
			Class genericReturnClass = getGenericTypeClass(object.getClass());

			String methodName = method.getName();
			//实际返回值类型
			Type resultType = method.getGenericReturnType();

			if (methodName.equals("toString")) {
				return "";
			}
			String[] interNamePaths = interfaceName.split("\\.");
			int len = interNamePaths.length;
			String simpleInterName = interNamePaths[len - 1];
			String url = getUrl(simpleInterName, method.getName());


			GetHeader getHeader = new GetHeader(method, args, methodName).invoke();
			Map<String, Object> postParameters = getHeader.getPostParameters();
			HttpHeaders headers = getHeader.getHeaders();

			Object paramsObj = new Object();

			String paramsJson = "{}";

			if (!postParameters.isEmpty()) {
				if (postParameters.size() == 1) {
					paramsObj = postParameters.values().iterator().next();
				} else {
					paramsObj = postParameters;
				}
			}
			log.info("postParameters=>{}", postParameters);
			paramsJson = JsonUtils.object2JsonString(paramsObj);
			HttpEntity<String> formEntity = new HttpEntity<String>(paramsJson, headers);
			log.info("formEntity=={}", formEntity);
			/**
			 * 获取返回值
			 */
			LsCloudResponse lsCloudResponse = getLsCloudResponse(url, restTemplate, formEntity);
			if (ResponseCodeEnum.FAIL.code().equals(lsCloudResponse.getCode())
			&& StringUtils.isEmpty(lsCloudResponse.getMsg())) {
				lsCloudResponse.setMsg("远程服务调用失败");
			}
			//完成目标类型的转换
			Object targetResult = handleResult(method, lsCloudResponse);
			String jsonResult = JsonUtils.object2JsonString(targetResult);
			targetResult = getTargetResult(genericReturnClass, resultType, targetResult, jsonResult);
			//一般类型
			log.info("反序列化后的对象===>{}", targetResult);
			return targetResult;
		}

		/**
		 * 查询返回值
		 *
		 * @param url
		 * @param restTemplate
		 * @param formEntity
		 * @return
		 */
		private LsCloudResponse getLsCloudResponse(String url, RestTemplate restTemplate, HttpEntity<String> formEntity) {
			long start = System.currentTimeMillis();
			LsCloudResponse lsCloudResponse = null;
			try {
				log.info("请求url=>{}", url);
				String lsReq = MDC.get("lsReq");
				if (StringUtils.isNotEmpty(lsReq)) {
					url = url + "?lsReq=" + lsReq;
				}
				lsCloudResponse = restTemplate.postForEntity(url, formEntity, LsCloudResponse.class).getBody();
				if (!ResponseCodeEnum.SUCCESS.code().equals(lsCloudResponse.getCode())) {
					throw new RuntimeException(lsCloudResponse.getMsg());
				}
			} catch (Exception e) {
				e.printStackTrace();
				String errorMsg = e.getMessage();
				if (StringUtils.isEmpty(errorMsg)) {
					Throwable throwable = e.getCause();
					if (null != throwable) {
						errorMsg = throwable.getMessage();
					}
				}
				if (StringUtils.isEmpty(errorMsg)) {
					errorMsg = "远程服务请求失败,请联系管理员--cllientStarter";
				}
				log.error("远程服务调用失败:{}", errorMsg);
				throw new RuntimeException(errorMsg);
			} finally {
				log.info("响应参数:【{}】,耗时:【{}】", lsCloudResponse, (System.currentTimeMillis() - start) + "毫秒");
			}
			return lsCloudResponse;
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
		private String getUrl(String simpleInterfaceName, String methodName) {
			StringBuilder urlBuilder = new StringBuilder();
			if (!serviceName.startsWith("http")) {
				urlBuilder.append("http://");
			}
			urlBuilder.append(serviceName);
			if (!serviceName.endsWith("/")) {
				urlBuilder.append("/");
			}
			//urlBuilder.append("lsCloud/execute");   0.0.1版本的使用方式

			urlBuilder.append(simpleInterfaceName).append("/").append(methodName);

			return urlBuilder.toString();
		}

		/**
		 * 获取头部信息
		 */
		private class GetHeader {
			private Method method;
			private Object[] args;
			private String methodName;
			private Map<String, Object> postParameters;
			private HttpHeaders headers;

			public GetHeader(Method method, Object[] args, String methodName) {
				this.method = method;
				this.args = args;
				this.methodName = methodName;
			}

			public Object[] getArgs() {
				return args;
			}

			public Map<String, Object> getPostParameters() {
				return postParameters;
			}

			public HttpHeaders getHeaders() {
				return headers;
			}

			public GetHeader invoke() {
				postParameters = new HashMap<>();

				Parameter[] parameters = method.getParameters();

				if (null != parameters) {
					int i = 0;
					for (Parameter parameter : parameters) {
						String paramName = parameter.getName();
						Object value = args[i];
						postParameters.put(paramName, value);
						i++;
					}
				}

				headers = new HttpHeaders();
				MediaType mediaType = MediaType.parseMediaType("application/json; charset=UTF-8");
				headers.setContentType(mediaType);
				headers.add("Accept", MediaType.APPLICATION_JSON.toString());
				return this;
			}
		}
	}

	/**
	 * 一系列转换,处理成目标对象
	 *
	 * @param genericReturnClass
	 * @param resultType
	 * @param targetResult
	 * @param jsonResult
	 * @return
	 */
	private static Object getTargetResult(Class genericReturnClass, Type resultType, Object targetResult, String jsonResult) {

		//泛型继承导致的拿不到真实类型的情况
		int classLen = 1;
		if (null != resultType) {
			String returnTypeName = resultType.getTypeName();
			classLen = returnTypeName.split("\\.").length;
		}

		if (resultType == null || resultType.getTypeName().equals(Object.class.getName()) || classLen == 1) {
			if (resultType.getTypeName().contains("List")) {
				List list = JsonUtils.json2Object(jsonResult, List.class);
				if (!CollectionUtils.isEmpty(list)) {
					List dtoList = new ArrayList<>();
					for (Object object1 : list) {
						Object dtoTarget = JsonUtils.json2Object(JsonUtils.object2JsonString(object1), genericReturnClass);
						dtoList.add(dtoTarget);
					}
					targetResult = dtoList;
				}
			} else {
				targetResult = JsonUtils.json2Object(jsonResult, genericReturnClass);
			}
		} else {
			targetResult = JsonUtils.json2Object(jsonResult, resultType);
		}
		return targetResult;
	}

	/**
	 * 是否是代理类
	 * @param thisObjClass
	 * @return
	 */
	private static boolean isProxyClass(Class thisObjClass) {
		if (thisObjClass.getName().toUpperCase().contains("CGLIB") || thisObjClass.getName().toUpperCase().contains("PROXY")) {
			return true;
		}
		return false;
	}

	/**
	 * 获取泛型名称
	 *
	 * @param thisObjClass
	 */
	private static Class getGenericTypeClass(Class thisObjClass) {

		if(isProxyClass(thisObjClass)) {
			Class superclass = thisObjClass.getSuperclass();
			if(null ==superclass || Object.class == superclass){
				thisObjClass = thisObjClass.getInterfaces()[0];
			}else{
				thisObjClass = superclass;
			}
		}

		Class genericClass = null;
		if (null != thisObjClass) {
			genericClass = GenericsUtils.getSuperClassGenricType(thisObjClass);
		}

		if (null == genericClass || genericClass == Object.class) {
			return null;
		}

		return genericClass;
	}

	/**
	 * 处理返回结果
	 *
	 * @param method
	 * @param lsCloudResponse
	 * @return
	 */
	private static Object handleResult(Method method, LsCloudResponse lsCloudResponse) {
		Object result = lsCloudResponse.getData();
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
