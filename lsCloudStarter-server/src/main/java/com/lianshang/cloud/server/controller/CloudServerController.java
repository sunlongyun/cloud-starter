package com.lianshang.cloud.server.controller;

import com.lianshang.cloud.server.beans.BaseRequest;
import com.lianshang.cloud.server.config.ServerStarterConfig;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@ConditionalOnMissingBean(CloudServerController.class)
@RequestMapping("/lsCloud")
public class CloudServerController {

  /**
   * 目标bean
   * @param baseRequest
   * @return
   */
  @ResponseBody
  @RequestMapping("/execute")
  public Object execute(@RequestBody BaseRequest baseRequest){
    if(null == baseRequest ||
        StringUtils.isEmpty (baseRequest.getMethodName ())
        || StringUtils.isEmpty (baseRequest.getInterfaceName ())) {
      return null;
    }else{
     Object target = ServerStarterConfig
         .execute (baseRequest.getInterfaceName (), baseRequest.getMethodName (), baseRequest.getParams ());
     return target;
    }
  }

  /**
   * 返回api列表
   * @return
   */
  @ResponseBody
  @RequestMapping("/")
  public List<Map<String, Object>> getApiList(){
    return ServerStarterConfig.getApiList ();
  }

}
