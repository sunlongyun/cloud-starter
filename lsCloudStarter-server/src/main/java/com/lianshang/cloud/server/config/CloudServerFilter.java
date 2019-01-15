package com.lianshang.cloud.server.config;

import com.lianshang.cloud.server.beans.LsCloudResponse;
import com.lianshang.cloud.server.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * 描述:
 * 1.服务类接口的名称作为controller请求路径
 * 2.服务类接口方法的名称获取controller中方法的请求路径
 * 3.controller方法的参数和service接口的方法参数完全相同
 *
 * @AUTHOR 孙龙云
 * @date 2018-12-19 上午9:52
 */
@Slf4j
@WebFilter(filterName = "cloudFilter", urlPatterns = "/*")
@Order(Ordered.LOWEST_PRECEDENCE + 10)
public class CloudServerFilter implements Filter {

    public static final String PROXY = "PROXY";
    public static final String CGLIB = "CGLIB";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("CloudServerFilter初始化-------");
    }


    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
        /**
         * 本系统所有发布的服务对象bean
         */
        Map<String, Object> serviceBeanMaps = ServerStarterConfig.getSimpleServiceMap();

        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        HttpServletResponse httpServletResponse = (HttpServletResponse) response;

        httpServletRequest.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        String requestPath = httpServletRequest.getServletPath();
        if (requestPath.startsWith("/")) {
            requestPath = requestPath.replaceAll("^\\/", "");
        }
        String[] paths = requestPath.split("\\/");

        if (paths.length == 2) {

            String beanName = paths[0];
            beanName = upperCase(beanName);
            String methodName = paths[1];
            int paramsLen = httpServletRequest.getParameterMap().size();
            Object targetBean = serviceBeanMaps.get(beanName);

            if (null != targetBean) {

                Class targetClazz = targetBean.getClass();
                if (isProxyClass(targetClazz)) {
                    targetClazz = targetClazz.getSuperclass();
                }
                Method method = getMethod(methodName, targetClazz, paramsLen);
                //从body体获取参数
                Object[] paramValues = getParamValuesByRequestBody(method, httpServletRequest);
                if (null == paramValues) {//如果body体没有取的参数,则尝试从parameter请求参数中获取
                    paramValues = getParamValuesByRequestParams(httpServletRequest, method);
                }

                log.info("请求入参:{}",JsonUtils.object2JsonString(paramValues));

                LsCloudResponse res = null;
                try {
                    Object targetResult = method.invoke(targetBean, paramValues);
                    res = LsCloudResponse.success(targetResult);
                } catch (Throwable e) {
                    log.error("服务端service执行失败:", e);

                    String errorMsg = e.getMessage();
                    if (org.springframework.util.StringUtils.isEmpty(errorMsg)) {
                        Throwable throwable = e.getCause();
                        errorMsg = throwable.getMessage();
                    }
                    if (org.springframework.util.StringUtils.isEmpty(errorMsg)) {
                        errorMsg = "远程服务调用失败,请联系管理员--CloudServerFilter";
                    }

                    e.printStackTrace();

                    res = LsCloudResponse.fail(errorMsg);
                }
                if (null == res) {
                    res = LsCloudResponse.success();
                }

                httpServletResponse.setContentType("application/json; charset=utf-8");
                httpServletResponse.getWriter().write(JsonUtils.object2JsonString(res));
                return;
            }
        }
        chain.doFilter(request, response);
    }


    /**
     * 首字母大写
     *
     * @param str
     * @return
     */
    private String upperCase(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    /**
     * 首字母小写
     *
     * @param str
     * @return
     */
    private String lowerCase(String str) {
        return str.substring(0, 1).toLowerCase() + str.substring(1);
    }
    /**
     * 获取目标函数
     *
     * @param targetClass
     * @return
     */
    private Method getMethod(String methodName, Class targetClass, int paramsLen) {
        Method[] methods = targetClass.getMethods();
        if (null != methods) {

            //先进行参数数量相同的精准匹配
            for (Method method : methods) {
                if (method.getParameters().length == paramsLen && method.getName().equals(methodName)) {
                    return method;
                }
            }

            //参数可能在body传递过来,paramsLen=0时可能有一个参数
            if (paramsLen == 0) {
                paramsLen = 1;
                for (Method method : methods) {
                    if (method.getParameters().length == paramsLen && method.getName().equals(methodName)) {
                        return method;
                    }
                }
            }

            //取名称相同的第一个
            for (Method method : methods) {
                if (method.getName().equals(methodName)) {
                    return method;
                }
            }
        }

        return null;
    }

    @Override
    public void destroy() {

    }

    /**
     * 是否是代理类
     *
     * @param thisObjClass
     * @return
     */
    private static boolean isProxyClass(Class thisObjClass) {
        if (thisObjClass.getName().toUpperCase().contains(CGLIB) || thisObjClass.getName().toUpperCase().contains(PROXY)) {
            return true;
        }
        return false;
    }

    /**
     * 从请求参数中获取目标参数值
     *
     * @param httpServletRequest
     * @param method
     * @return
     */
    private Object[] getParamValuesByRequestParams(HttpServletRequest httpServletRequest, Method method) {
        Enumeration<String> paramNames = httpServletRequest.getParameterNames();
        Parameter[] parameters = method.getParameters();
        int paramsSize = method.getParameterCount();
        Object[] paramValues = new Object[paramsSize];
        int i = 0;
        while (paramNames.hasMoreElements()) {
            String paramName = paramNames.nextElement();
            Object value = httpServletRequest.getParameter(paramName);
            Parameter parameter = parameters[i];
            Class parameterClass = parameter.getType();
            if (value != null && parameterClass != Object.class && parameterClass != Serializable.class) {
                value = JsonUtils.json2Object(JsonUtils.object2JsonString(value), parameterClass);
            }
            paramValues[i] = value;
            i++;
        }
        return paramValues;
    }

    /**
     * 从body获取实际参数值
     *
     * @param method
     * @param httpServletRequest
     * @return
     */
    private Object[] getParamValuesByRequestBody(Method method, HttpServletRequest httpServletRequest) {

        if (method != null) {
            int paramsSize = method.getParameterCount();
            //首先尝试从body中取值
            String jsonBody = getBodyStrFromRequest(httpServletRequest);
            Parameter[] parameters = method.getParameters();
            //转为map对象
            if (StringUtils.isNotEmpty(jsonBody)) {//可以取到值,尝试反序列化到真实的类型
                Object[] pValues = new Object[paramsSize];

                //只有一个参数
                if (paramsSize == 1) {
                    Class paramClass = parameters[0].getType();
                    Object value = null;
                    if (paramClass != Object.class && paramClass != Serializable.class) {
                        value = JsonUtils.json2Object(jsonBody, paramClass);
                    }else{
                        value = jsonBody;
                    }

                    pValues[0] = value;
                    return pValues;
                }


                //尝试转为真实的参数类型
                int i = 0;
                Map<String, Object> dataMap = JsonUtils.json2Object(jsonBody, HashMap.class);
                if(null == dataMap || dataMap.isEmpty()){
                    return pValues;
                }

                for (Parameter parameter : parameters) {
                    Class paramClass = parameter.getType();
                    String paramName = parameter.getName();
                    Object value = dataMap.get(paramName);

                    if(null == value){
                        paramName = lowerCase(paramClass.getSimpleName());
                        value = dataMap.get(paramName);
                    }

                    if (null != value && paramClass != Object.class && paramClass != Serializable.class) {
                        value = JsonUtils.json2Object(JsonUtils.object2JsonString(value), paramClass);
                    }else{
                        value = JsonUtils.object2JsonString(value);
                    }
                    pValues[i] = value;
                    i++;
                }
                return pValues;
            }
        }
        return null;
    }

    /**
     * 从request获取json字符串
     *
     * @param request
     * @return
     */
    private String getBodyStrFromRequest(HttpServletRequest request) {
        try {
            BufferedReader streamReader = new BufferedReader(new InputStreamReader(request.getInputStream(),
              "UTF-8"));

            StringBuilder responseStrBuilder = new StringBuilder();
            String inputStr;
            while ((inputStr = streamReader.readLine()) != null)
                responseStrBuilder.append(inputStr);

            return responseStrBuilder.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
