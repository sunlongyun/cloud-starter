package com.lianshang.cloud.server.controller;

import com.lianshang.cloud.server.config.Response;
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
    public Response actuatorInfo(){
        return Response.success("success info------");
    }
}
