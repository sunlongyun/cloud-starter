//package com.lianshang.cloud.server.config;
//
//import com.lianshang.cloud.server.annotation.LsCloudService;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.BeansException;
//import org.springframework.beans.factory.BeanInitializationException;
//import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
//import org.springframework.beans.factory.support.BeanDefinitionBuilder;
//import org.springframework.beans.factory.support.BeanDefinitionRegistry;
//import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
//import org.springframework.beans.factory.support.GenericBeanDefinition;
//import org.springframework.context.ApplicationContext;
//import org.springframework.context.ApplicationContextAware;
//import org.springframework.util.CollectionUtils;
//
//import java.lang.reflect.Type;
//import java.util.Iterator;
//import java.util.Map;
//
///**
// * 动态注册controller层服务
// */
//@Slf4j
//public class ControllerServiceRegistar implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware {
//    private ApplicationContext applicationContext;
//
//    /**
//     * Modify the application context's internal bean definition registry after its
//     * standard initialization. All regular bean definitions will have been loaded,
//     * but no beans will have been instantiated yet. This allows for adding further
//     * bean definitions before the next post-processing phase kicks in.
//     *
//     * @param registry the bean definition registry used by the application context
//     * @throws BeansException in case of errors
//     */
//    @Override
//    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
//        Map<String, Object> serviceBeanMaps = applicationContext.getBeansWithAnnotation(LsCloudService.class);
//        if (!CollectionUtils.isEmpty(serviceBeanMaps)) {
//            Iterator<Object> beans = serviceBeanMaps.values().iterator();
//            while (beans.hasNext()) {
//                Object target = beans.next();
//                registerControllerBean(target,registry);
//            }
//        }
//        serviceBeanMaps.values();
//    }
//
//    /**
//     * 注册controllerbean
//     *
//     * @param target
//     */
//    private void registerControllerBean(Object target, BeanDefinitionRegistry registry) {
//        Type superType = target.getClass().getGenericSuperclass();
//        if (null == superType || superType.getTypeName().equals(Object.class.getName())) {
//            superType = target.getClass().getGenericInterfaces()[0];
//        }
//
//        Class superClass = superType.getClass();
//        String superClassName = superClass.getName();
//        String[] splitPaths = superClassName.split("\\.");
//        String controllerPath = splitPaths[splitPaths.length - 1];
//        //注册controller
//        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(ServiceController.class);
//        GenericBeanDefinition definition = (GenericBeanDefinition) builder.getRawBeanDefinition();
//        registry.registerBeanDefinition(controllerPath,definition);
//    }
//
//    /**
//     * Modify the application context's internal bean factory after its standard
//     * initialization. All bean definitions will have been loaded, but no beans
//     * will have been instantiated yet. This allows for overriding or adding
//     * properties even to eager-initializing beans.
//     *
//     * @param beanFactory the bean factory used by the application context
//     * @throws BeansException in case of errors
//     */
//    @Override
//    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
//        log.info("ControllerServiceRegistar执行!-------");
//    }
//
//    /**
//     * Set the ApplicationContext that this object runs in.
//     * Normally this call will be used to initialize the object.
//     * <p>Invoked after population of normal bean properties but before an init callback such
//     *
//     * @throws BeansException if thrown by application context methods
//     * @see BeanInitializationException
//     */
//    @Override
//    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
//        log.info("获取applicationContext------");
//        this.applicationContext = applicationContext;
//    }
//}
