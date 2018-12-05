package com.lianshang.cloud.client.config;

import com.lianshang.cloud.client.annotation.LsCloudAutowired;
import com.lianshang.cloud.client.beans.LsCloudResponse;
import com.lianshang.cloud.client.enums.ResponseCodeEnum;
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
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 服务消费者配置管理
 */
@Slf4j
@EnableEurekaClient
@EnableDiscoveryClient
@ConditionalOnMissingBean(ClientStartConfig.class)
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
		log.info("ClientStarter===>初始化-------");
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
		if (targetClzz.getName().contains("CGLIB")
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

				String methodName = method.getName();
			    Type resultType = method.getGenericReturnType();


				if (methodName.equals("toString")) {
					return interfaceName;
				}
				String url = getUrl();

				RestTemplate restTemplate = getRestTemplate();

				Map<String, Object> postParameters = new HashMap<>();
				postParameters.put("methodName", methodName);
				postParameters.put("interfaceName", interfaceName);

				if(null == args) args = new Object[0];
				List<String> realTypeName = new ArrayList<>();
				Class<?>[] parameterTypes = method.getParameterTypes();

				int len = args.length;
				for (int i = 0; i < len; i++) {
					String argName = args[i].getClass().getName();
					realTypeName.add(argName);
				}


				List<String> paramsParamTypeNames = new ArrayList<>();
				for(Class superType : parameterTypes){
					paramsParamTypeNames.add(superType.getName());
				}
				postParameters.put("params", Arrays.asList(args));
				postParameters.put("paramTypeNames", realTypeName);

				postParameters.put("paramsParamTypeNames", paramsParamTypeNames);
				
				HttpHeaders headers = new HttpHeaders();
				MediaType mediaType = MediaType.parseMediaType("application/json; charset=UTF-8");
				headers.setContentType(mediaType);
				headers.add("Accept", MediaType.APPLICATION_JSON.toString());

				String paramsJson = JsonUtils.object2JsonString(postParameters);
				HttpEntity<String> formEntity = new HttpEntity<String>(paramsJson, headers);

			    LsCloudResponse lsCloudResponse = null;
				long start = System.currentTimeMillis();
				try {
					log.info("请求参数=>{}", paramsJson);
					log.info("请求url=>{}", url);
					log.info("lsReq==>{}", MDC.get("lsReq"));
					url = url + "?lsReq=" + MDC.get("lsReq");
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
			String returnTypeName = lsCloudResponse.getReturnTypeName();

				//完成目标类型的转换
			Object targetResult = handleResult(method, lsCloudResponse);
			String JsonResult = JsonUtils.object2JsonString(targetResult);

			//某些方法特殊处理
			Object dtoList = getSomeMethodTarget(args, methodName, JsonResult);
			if (dtoList != null){
				log.info("反序列化后的对象===>{}", dtoList);
				return dtoList;
			}

			Class clazz = Class.forName(returnTypeName);
			targetResult = JsonUtils.json2Object(JsonResult, Class.forName(resultType.getTypeName()));

			log.info("反序列化后的对象===>{}", targetResult);
			return targetResult;
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
	 * 某些方法特殊处理
	 * @param args
	 * @param methodName
	 * @param jsonResult
	 * @return
	 */
	private static Object getSomeMethodTarget(Object[] args, String methodName, String jsonResult) {
		if ((methodName.equals("getById")
        || methodName.equals("getListByIds")
        || methodName.equals("getList")) && args.length == 2) {
            Object dtoClassName = args[1];
            if (dtoClassName.toString().contains("Dto")) {
                try {
                    Class dtoClass = Class.forName(dtoClassName.toString());
                    if (methodName.equals("getListByIds") || methodName.equals("getList")) {
                        List list = JsonUtils.json2Object(jsonResult, List.class);
                        if (!CollectionUtils.isEmpty(list)) {
                            List dtoList = new ArrayList<>();
                            for (Object object1 : list) {
                                Object dtoTarget = JsonUtils.json2Object(JsonUtils.object2JsonString(object1), dtoClass);
                                dtoList.add(dtoTarget);
                            }
                            return dtoList;
                        }
                    }else if(methodName.equals("getById")){
                        Object dtoTarget = JsonUtils.json2Object(jsonResult, dtoClass);
                        return  dtoTarget;
                    }
                } catch (Exception ex) {

                }

            }
        }
		return null;
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
