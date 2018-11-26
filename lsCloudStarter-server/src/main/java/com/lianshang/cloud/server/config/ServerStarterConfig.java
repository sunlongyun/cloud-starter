package com.lianshang.cloud.server.config;

import com.lianshang.cloud.server.annotation.LsCloudService;
import com.lianshang.cloud.server.beans.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.*;

/**
 * 服务提供方,配置管理
 */
@Slf4j
@EnableEurekaClient
@ConditionalOnMissingBean(ServerStarterConfig.class)
public class ServerStarterConfig
		implements ApplicationListener<ContextRefreshedEvent>, ApplicationContextAware {

	private ApplicationContext applicationContext;
	private static Map<String, Object> cloudServiceMap = new HashMap<>();
	ServerStarterConfig(){
		log.info("初始化-------------");
	}
	/**
	 * 保存服务发布者的bean
	 */
	@Override
	public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {

		Map<String, Object> cloudServices = applicationContext.getBeansWithAnnotation(LsCloudService.class);
		if (!CollectionUtils.isEmpty(cloudServices)) {
			Collection<?> values = cloudServices.values();
			for (Object v : values) {
				Class<?> targetClass = v.getClass();
				if(targetClass.getName().contains("CGLIB")){
					targetClass = targetClass.getSuperclass();
				}
				Class<?>[] inters = targetClass.getInterfaces();
				for (Class inter : inters) {
					cloudServiceMap.put(inter.getName(), v);
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
	public static Object execute(String interfaceName, String methodName, List<Object> params) {
		
		log.info("interfaceName=>{}",interfaceName);
		Object bean = getBean( cloudServiceMap,interfaceName);
		if (bean == null) {
			log.error("未找接口对应的bean==>{}", interfaceName);
			return Response.fail ("未找接口对应的bean【" + interfaceName + "】");
		}
		Class<?> clzz = bean.getClass();
		Method[] methods = clzz.getMethods();
		try {
			Object[] targetParams = new Object[params.size()];
			ArrayList<Object> arrayList = (ArrayList<Object>) params;
			arrayList.toArray(targetParams);

			for (Method method : methods) {
				if (method.getName().equals(methodName) && method.getParameterTypes().length == params.size()) {
					Object value = method.invoke(bean, targetParams);
					return Response.success(value);
				}
			}
			return Response.fail ("未找到bean【" + interfaceName + "】中符合条件的方法【" + methodName + "】");
		} catch (Exception e) {
			log.error("服务端service执行失败,{}", e);
			e.printStackTrace();
			String errorMsg = e.getMessage ();
			if(StringUtils.isEmpty (errorMsg)){
				errorMsg = e.getLocalizedMessage ();
			}
			return Response.fail ("调用service失败【" + errorMsg + "】");
		}
	}

	/**
	 * 查询函数对象
	 *
	 * @param interClzz
	 * @param methodName
	 * @return
	 */
	private static Method getMethod(Class<?> interClzz, String methodName) {
		Method[] methods = interClzz.getDeclaredMethods();
		if (null != methods) {
			for (Method m : methods) {
				if (m.getName().equals(methodName)) {
					return m;
				}
			}
		}
		methods = interClzz.getInterfaces()[0].getDeclaredMethods();
		if (null != methods) {
			for (Method m : methods) {
				if (m.getName().equals(methodName)) {
					return m;
				}
			}
		}
		return null;
	}

	private static Object getBean(Map<String, Object> cloudServiceMap, String interfaceName) {
		Object bean = cloudServiceMap.get(interfaceName);
		if(null != bean){
			return bean;
		}

		Iterator<String> it = cloudServiceMap.keySet().iterator();
		while(it.hasNext()){
			String key = it.next();
			bean = cloudServiceMap.get(key);
			Class<?> targetClass = bean.getClass();
			//还原真实实现类
			if(targetClass.getName().contains("CGLIB")){
				targetClass = targetClass.getSuperclass();
			}
			//获取接口 类
			Class<?>[] interfaces = targetClass.getInterfaces();
			for(Class<?> clzz : interfaces){
				String clazzName = clzz.getName();
				log.info("clzzName:{}, interfaceName:{}", clazzName, interfaceName);
				if(null !=  targetClass && clazzName.equals(interfaceName)){
					return bean;
				}
			}
		}
		return null;
	}
public static void main(String[] args) {
	String x = "com.lianshang.cloth2.store.service.impl.ClothStoreServiceImpl$$EnhancerBySpringCGLIB$$1cc820dc";
	String y = "com.lianshang.cloth2.store.service";
	System.out.println(x.contains(y));
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
}
