package com.lianshang.cloud.server.config;

import java.util.Map;
import java.util.UUID;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.lianshang.cloud.server.annotation.EnableSeriesLogs;
import com.lianshang.cloud.server.utils.CRC8Util;

public class SeriesLogsConfigRegistrar implements ImportBeanDefinitionRegistrar {

  private final String DEFAULT_PARAM_NAME = "lsReq";

  @Override
  public void registerBeanDefinitions(AnnotationMetadata annotationMetadata,
      BeanDefinitionRegistry beanDefinitionRegistry) {
    Map<String, Object> anMap = annotationMetadata.getAnnotationAttributes (EnableSeriesLogs.class.getName ());
    String paramName = (String) anMap.get ("value");
    if (StringUtils.isEmpty (paramName)) {
      paramName = DEFAULT_PARAM_NAME;
    }

    //手动注入 Forwards 类的实例
    BeanDefinitionBuilder clienLoginterceptor = BeanDefinitionBuilder.rootBeanDefinition (ClientLogInteceptor.class);
    clienLoginterceptor.addPropertyValue ("paramName", paramName);
    beanDefinitionRegistry.registerBeanDefinition ("clienLoginterceptor", clienLoginterceptor.getBeanDefinition ());
  }

  private static class ClientLogInteceptor implements WebMvcConfigurer {

    private String paramName;

    public void setParamName(String paramName) {
      this.paramName = paramName;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
      registry.addInterceptor (new HandlerInterceptor () {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
          String lsReq = getLsReq (request);
          MDC.put ("lsReq", lsReq);
          return true;
        }

        /**
         * 获取lsReq
         * @param request
         * @return
         */
        private String getLsReq(HttpServletRequest request) {
          String lsReq = request.getHeader (paramName);
          if (StringUtils.isEmpty (lsReq)) {
            lsReq = request.getParameter ("lsReq");
          }
          if (StringUtils.isEmpty (lsReq)) {
            lsReq = getValueFromCookie (request, paramName);
          }
          if (StringUtils.isEmpty (lsReq)) {
            lsReq = UUID.randomUUID () + "_"
                + CRC8Util.calcCrc8 ((System.currentTimeMillis () + "").getBytes ());
          }
          if (!lsReq.startsWith ("【")) {
            lsReq = "【" + lsReq + "】";
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
          Cookie[] cookies = request.getCookies ();
          if (cookies != null) {
            // 遍历数组
            for (Cookie cookie : cookies) {
              if (cookie.getName ().equals (lsReq)) {
                // 取出cookie的值
                String value = cookie.getValue ();
                return value;
              }
            }
          }
          return null;
        }

        @Override
        public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
            ModelAndView modelAndView) throws Exception {
          MDC.remove ("lsReq");
        }

      });
    }
  }
}
