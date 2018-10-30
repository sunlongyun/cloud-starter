package com.lianshang.cloud.server.controller;

import com.lianshang.cloud.server.beans.Response;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.lianshang.cloud.server.beans.BaseRequest;
import com.lianshang.cloud.server.config.ServerStarterConfig;

import lombok.extern.slf4j.Slf4j;

@ConditionalOnMissingBean(CloudServerController.class)
@RequestMapping("/lsCloud")
@Slf4j
public class CloudServerController {

  /**
   * 目标bean
   */
  @ResponseBody
  @RequestMapping("/execute")
  public Object execute(@RequestBody BaseRequest baseRequest, HttpServletRequest request) {

    if (null == baseRequest || StringUtils.isEmpty (baseRequest.getMethodName ())
        || StringUtils.isEmpty (baseRequest.getInterfaceName ())) {
      log.error ("请求参数都不能为空");
      return Response.fail ("请求参数都不能为空");
    } else {
      Object target = null;
      long start = System.currentTimeMillis ();
      try {
        log.info ("请求参数==>{}", baseRequest);
        target = ServerStarterConfig.execute (baseRequest.getInterfaceName (),
            baseRequest.getMethodName (), baseRequest.getParams ());
        if(null == target){
          throw new RuntimeException("服务请求异常");
        }
      } catch (Exception e) {
        log.error ("服务异常,", e);
        return Response.fail ("服务异常:" + e.getMessage ());
      } finally {
        log.info ("响应参数==>【{}】,耗时==》【{}】", target, (System.currentTimeMillis () - start) + "毫秒");
      }
      return target;
    }
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
