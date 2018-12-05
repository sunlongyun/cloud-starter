package com.lianshang.cloud.server.controller;

import com.lianshang.cloud.server.beans.BaseRequest;
import com.lianshang.cloud.server.beans.LsCloudResponse;
import com.lianshang.cloud.server.config.ServerStarterConfig;
import com.lianshang.cloud.server.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConditionalOnMissingBean(CloudServerController.class)
@RequestMapping("/lsCloud")
@Slf4j
public class CloudServerController {

  /**
   * 目标bean
   */
  @ResponseBody
  @RequestMapping(value = "/execute", produces = "application/json")
  public Object execute(@RequestBody BaseRequest baseRequest, HttpServletRequest request) {

    if (null == baseRequest || StringUtils.isEmpty (baseRequest.getMethodName ())
      || StringUtils.isEmpty (baseRequest.getInterfaceName ())) {
      log.error ("请求参数都不能为空");
      return LsCloudResponse.fail ("请求参数都不能为空");
    } else {
      Object target = null;
      long start = System.currentTimeMillis ();
      try {
        log.info ("请求参数==>{}", baseRequest);
        List<Object> params = getPrams(baseRequest);
        List<String> paramTypeNameList = baseRequest.getParamTypeNames();
        if(null == paramTypeNameList){
          paramTypeNameList = new ArrayList<>();
        }
        target = ServerStarterConfig.execute (baseRequest.getInterfaceName (),
          baseRequest.getMethodName(),  params, paramTypeNameList);
        if(null == target){
          throw new RuntimeException("服务请求异常");
        }
      } catch (Exception e) {
        log.error ("服务异常,", e);
        return LsCloudResponse.fail ("服务异常:" + e.getMessage ());
      } finally {
        log.info ("响应参数==>【{}】,耗时==》【{}】", target, (System.currentTimeMillis () - start) + "毫秒");
      }
      return target;
    }
  }

  /**
   * 获取真实对象
   *
   * @param baseRequest
   * @return
   * @throws ClassNotFoundException
   * @throws InstantiationException
   * @throws IllegalAccessException
   */
  private List<Object> getPrams(@RequestBody BaseRequest baseRequest) {
    List<Object> params = new ArrayList<>();
    //参数转换回来
    List<String> realParamTypeName = baseRequest.getParamTypeNames();
    //父类参数类型,处理代理类
    List<String> parentParamTypeName =  baseRequest.getParamsParamTypeNames();

    if (null == realParamTypeName) {
      realParamTypeName = new ArrayList<>();
    }
    if(null == parentParamTypeName){
      parentParamTypeName = new ArrayList<>();
    }
    List<Object> valueParams = baseRequest.getParams();
    if (valueParams == null) {
      valueParams = new ArrayList<>();
    }
    int len = realParamTypeName.size();

    try {

      for (int i = 0; i < len; i++) {
        Object object = valueParams.get(i);
        String typeName = realParamTypeName.get(i);
        String jsonValue = JsonUtils.object2JsonString(object);

        Class paramType = Class.forName(typeName);
        Object obj = null;
        try {
          obj = JsonUtils.json2Object(jsonValue, paramType);
          if(null == obj){
            String parentTypeName  =  parentParamTypeName.get(i);
            paramType = Class.forName(parentTypeName);
            obj = JsonUtils.json2Object(jsonValue, paramType);
          }
        } catch (Exception ex) {

        }

        params.add(obj);
      }
    } catch (Exception ex) {
      ex.printStackTrace();
    }

    return params;
  }

  /**
   * 获取对象
   *
   * @param linkedHashMaps
   * @param index
   * @param clzz
   * @return
   * @throws InstantiationException
   * @throws IllegalAccessException
   */
  private Object getObj(List<LinkedHashMap> linkedHashMaps, int index, Class clzz) throws InstantiationException, IllegalAccessException {
    Object object = clzz.newInstance();
    Field[] fields = clzz.getDeclaredFields();

    if (null != fields) {
      LinkedHashMap linkedHashMap = linkedHashMaps.get(index);
      for (Field field : fields) {
        String fName = field.getName();
        Object value = linkedHashMap.get(fName);
        field.setAccessible(true);
        field.set(object, value);
        field.setAccessible(false);
      }
    }

    return object;
  }

  /**
   * 返回api列表
   */
  @ResponseBody
  @RequestMapping("/")
  public List<Map<String, Object>> getApiList() {
    return ServerStarterConfig.getApiList ();
  }

}
