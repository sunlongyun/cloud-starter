package com.lianshang.cloud.server.controller;

import com.lianshang.cloud.server.beans.LsCloudResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 描述:
 *
 * @AUTHOR 孙龙云
 * @date 2018-12-24 下午1:21
 */
@RestController
public class CloudServiceController {

    @RequestMapping("/actuator/info")
    public LsCloudResponse actuatorInfo(){
        return LsCloudResponse.success("服务端运行正常-------");
    }
}
