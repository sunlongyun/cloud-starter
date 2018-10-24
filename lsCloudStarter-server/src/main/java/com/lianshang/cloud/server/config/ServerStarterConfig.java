package com.lianshang.cloud.server.config;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import com.lianshang.cloud.server.annotation.LsCloudService;
import org.springframework.beans.BeansException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.util.CollectionUtils;

/**
 * 服务提供方,配置管理
 */
@Slf4j
@EnableEurekaClient
@ConditionalOnMissingBean(ServerStarterConfig.class)
public class ServerStarterConfig implements ApplicationListener<ContextRefreshedEvent>, ApplicationContextAware{
  private ApplicationContext applicationContext;
  private static Map<String, Object> cloudServiceMap = new HashMap<> ();

  /**
   * 保存服务发布者的bean
   * @param contextRefreshedEvent
   */
  @Override
  public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {

    Map<String, Object> cloudServices =  applicationContext.getBeansWithAnnotation (LsCloudService.class);
    if(!CollectionUtils.isEmpty (cloudServices) ){
      Collection<?> values = cloudServices.values ();
      for(Object v : values){
        Class<?>[] inters = v.getClass ().getInterfaces ();
        for(Class inter : inters){
          if(inter.isInterface ()){
            cloudServiceMap.put (inter.getName (), v);
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
   * @param interfaceName
   * @param methodName
   * @param params
   * @return
   */
  public static  Object execute(String interfaceName, String methodName, Object ... params){

    Object bean = cloudServiceMap.get (interfaceName);
    if(bean == null){
      log.error ("未找接口对应的bean==>{}", interfaceName);
      return null;
    }
    Method[] methods = bean.getClass ().getMethods ();
    for(Method method : methods){
      if(method.getName ().equals (methodName) && method.getParameterTypes ().length == params.length){
       try {
        Object value = method.invoke (bean, params);
        return value;
       }catch (Exception e){
         log.error ("调用service失败,{}", e);
         e.printStackTrace ();
       }
      }
    }
    log.error ("调用service失败,{}", interfaceName);
    return null;
  }
}
