package com.lianshang.cloud.server.config;

import com.lianshang.cloud.server.beans.LsCloudResponse;
import com.lianshang.cloud.server.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterNameDiscoverer;
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
import java.lang.reflect.Type;
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
    public static final String LS_REQ = "lsReq";
    private ThreadLocal<String> reqBodyLocal = new ThreadLocal<>();
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

            Map<String, String[]> requestMap = httpServletRequest.getParameterMap();
            int paramsLen = requestMap.size();
            Object targetBean = serviceBeanMaps.get(beanName);
            //做串联日志的参数不能参与选择方法的计算
            if (requestMap.containsKey(LS_REQ)) {
                paramsLen = paramsLen - 1;
            }
            if (null != targetBean) {
                handleTargetBean(httpServletRequest, httpServletResponse, methodName, paramsLen, targetBean);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * 处理返回结果
     * @param httpServletRequest
     * @param httpServletResponse
     * @param methodName
     * @param paramsLen
     * @param targetBean
     * @throws IOException
     */
    private void handleTargetBean(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, String methodName, int paramsLen, Object targetBean) throws IOException {

        Class targetClazz = targetBean.getClass();

        if (isProxyClass(targetClazz)) {
            targetClazz = targetClazz.getSuperclass();
        }

        Method method = getMethod(methodName, targetClazz, paramsLen, httpServletRequest);
        //从body体获取参数
        Object[] paramValues = getParamValuesByRequestBody(method, httpServletRequest);
        if (null == paramValues) { //如果body体没有取的参数,则尝试从parameter请求参数中获取
            paramValues = getParamValuesByRequestParams(httpServletRequest, method);
        }

        reqBodyLocal.remove();

        log.info("请求入参:{}", JsonUtils.object2JsonString(paramValues));

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
                errorMsg = "远程服务调用失败,请联系管理员!";
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
    private Method getMethod(String methodName, Class targetClass, int paramsLen, HttpServletRequest httpServletRequest) {

        Method[] methods = targetClass.getMethods();

        String jsonBody = getBodyStrFromRequest(httpServletRequest);
        log.info("jsonBody=>{}", jsonBody);
        if (StringUtils.isNotEmpty(jsonBody)) {
            jsonBody = jsonBody.replaceAll("\\{", "")
              .replaceAll("\\}", "");
            if (StringUtils.isNotEmpty(jsonBody)) {
                paramsLen = 1;//防止获取到空参数的函数
            }
        }

        if (null != methods) {

                //先比较参数长度相同的,client客户端会把真实的参数长度传递过来
                for (Method method : methods) {
                    int len = method.getParameters().length;
                    if (method.getName().equals(methodName) && len == paramsLen) {
                        return method;
                    }
                }

                //feign调用,null参数丢失的话,寻找符合条件的第一个方法
                for (Method method : methods) {
                    int len = method.getParameters().length;
                    if (method.getName().equals(methodName) && len > paramsLen) {
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

        if (null == method) {
            return null;
        }
        Parameter[] parameters = method.getParameters();
        int paramsSize = method.getParameterCount();
        Object[] paramValues = new Object[paramsSize];

        if (paramsSize == 0) return paramValues;

        int i = 0;
        while (paramNames.hasMoreElements()) {

            String paramName = paramNames.nextElement();

            if (LS_REQ.equals(paramName)) {
                continue;
            }

            Object value = httpServletRequest.getParameter(paramName);
            Parameter parameter = parameters[i];
            Class parameterClass = parameter.getType();
            Type parameterizedType = parameter.getParameterizedType();

            if (value != null && parameterClass != Object.class && parameterClass != Serializable.class) {
                value = JsonUtils.json2Object(JsonUtils.object2JsonString(value), parameterizedType);
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

            //转为map对象,  可以取到值,尝试反序列化到真实的类型
            if (StringUtils.isNotEmpty(jsonBody)) {

                Object[] pValues = new Object[paramsSize];

                //只有一个参数
                if (HandleSingleParam(paramsSize, jsonBody, parameters, pValues)) return pValues;

                //多个参数
                Map<String, Object> dataMap = JsonUtils.json2Object(jsonBody, HashMap.class);
                if(null == dataMap || dataMap.isEmpty()){
                    return pValues;
                }
                if (getResultValues(method, parameters, pValues, dataMap)) return null;

                return pValues;
            }
        }
        return null;
    }

    /**
     * 一个参数解析
     * @param paramsSize
     * @param jsonBody
     * @param parameters
     * @param pValues
     * @return
     */
    private boolean HandleSingleParam(int paramsSize, String jsonBody, Parameter[] parameters, Object[] pValues) {
        if (paramsSize == 1) {
            Type parameterizedType = parameters[0].getParameterizedType();
            Class paramClass = parameters[0].getType();
            log.info("type==>{}", parameterizedType);
            Object value = null;

            if (paramClass != Object.class && paramClass != Serializable.class) {
                value = JsonUtils.json2Object(jsonBody, parameterizedType);
            }
            if(null == value){
                value = jsonBody;
            }
            pValues[0] = value;
            return true;
        }
        return false;
    }

    /**
     * 获取 真实的返回结果 pValues
     * @param method
     * @param parameters
     * @param pValues
     * @param dataMap
     * @return
     */
    private boolean getResultValues(Method method, Parameter[] parameters, Object[] pValues,
                                    Map<String, Object> dataMap) {


        ParameterNameDiscoverer pnd = new LocalVariableTableParameterNameDiscoverer();
        String[] parameterNames =  pnd.getParameterNames(method);
        if(null == parameterNames || parameterNames.length==0){
            return true;
        }

        int i = 0;
        for (Parameter parameter : parameters) {

            Type parameterizedType = parameter.getParameterizedType();
            Class paramClass = parameter.getType();
            String paramName = parameterNames[i];

            Object value = dataMap.get(paramName);

            if(null == value){
                paramName = lowerCase(paramClass.getSimpleName());
                value = dataMap.get(paramName);
            }

            if (null != value && paramClass != Object.class && paramClass != Serializable.class) {
                value = JsonUtils.json2Object(JsonUtils.object2JsonString(value), parameterizedType);
            }else{
                value = JsonUtils.object2JsonString(value);
            }
            pValues[i] = value;
            i++;
        }
        return false;
    }

    /**
     * 从request获取json字符串
     *
     * @param request
     * @return
     */
    private String getBodyStrFromRequest(HttpServletRequest request) {

        String reqBody = reqBodyLocal.get();

        if (StringUtils.isEmpty(reqBody)) {
            try {

                BufferedReader streamReader = new BufferedReader(new InputStreamReader(request.getInputStream(),
                  "UTF-8"));

                StringBuilder responseStrBuilder = new StringBuilder();
                String inputStr;
                while ((inputStr = streamReader.readLine()) != null)
                    responseStrBuilder.append(inputStr);

                reqBody = responseStrBuilder.toString();

                reqBodyLocal.set(reqBody);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return reqBody;
    }
}
