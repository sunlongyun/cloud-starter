# cloud-starter

本插件是基于springcloud封装的远程服务调用组件,旨在简化springcloud原生的远程服务调用代码复杂度(服务端不用写controller发布服务,客户端不用使用feign或者ribbon注册服务),从而提升开发效率.

本插件只封装简化了springcloud的"服务注册与发现",不影响其他功能的使用,同时也不影响程序员继续使用原生的"服务注册与发现".

本插件提示了跨平台的日志追踪功能.只需要@EnableSeriesLogs简单的一个注解,就可以实现同一次请求的不同平台的日志具有相同的前缀,方便日志追踪.

插件由 客户端client-starter插件和服务端server-starter插件两部分组成.两个组件配合使用.

注意:使用了本插件,无需在进行springcloud相关maven依赖的的的引用和相关注解的开启.           



使用方法:

1.服务端发布服务

	1.1 pom引入maven依赖   
		<dependency>
			<groupId>com.lianshang.cloud</groupId>
			<artifactId>cloud-server-starter</artifactId>
			<version>${cloud-server-starter.version}</version>
		</dependency>

		当前最新版本 0.0.2



	1.2  对service实现类添加注解 @LsCloudService
	例如:
	/**
	 * @author: lianshang
	 **/
	@LsCloudService
	public class PayServiceImpl implements IPayService {

	.....


	}

	此刻,已经完成了服务端service的发布.



2.客户端使用远程服务

	2.1 pom 引入maven依赖
	<dependency>
		<groupId>com.lianshang.cloud</groupId>
		<artifactId>cloud-client-starter</artifactId>
		<version>${cloud-client-starter.version}</version>
	</dependency>

	当前最新版本 0.0.2


	2.2 客户端使用远程service服务,直接用 @LsCloudAutowired("服务名称")

	@RestController
	@RequestMapping("order")
	public class OrderController {

         ///CLOTH2-BIZ-SERVICE 为eureka注册中心中服务(应用)名称
  	@LsCloudAutowired("CLOTH2-BIZ-SERVICE")
  	private OrderService orderService;

  	.....

  	}



3.提供了跨平台的日志串联(非必须,只有需要开启跨平台日志,才进行配置)

  基于MDC提供了跨平台连续日志的功能,便于日志的追踪.同一个平台中,同一次请求的产生的日志都有相同的前缀;跨平台的远程服务调用,如果客户端和服务端都开启了跨平台连续日志功能.不同平台的日志也具有相同的前缀,从而便于不同平台的同一次请求的日志追踪.

  3.1 服务端或者客户端使用 @EnableSeriesLogs注解可以开启当前平台的串联日志

  例如:

  	@EnableSeriesLogs
	public class SharpWebConfig implements WebMvcConfigurer {
      .........

      }



  3.2 logback.xml(或者log4j)的日志配置中添加 %X{lsReq}
  
  例如:
   <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="ch.qos.logback.classic.encoder.PatternLayoutEncoder">
            <pattern>%X{lsReq} %d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger -%line - %msg%n</pattern>
        </encoder>
    </appender>
    
    
  打印日志举例:
	  客户端日志:
	 【a660f9b2-a669-4b56-b585-1b8369d4efcc_-55】 2018-12-17 15:00:36.809 [http-nio-8080-exec-1] INFO  com.lianshang.common.controller.aspect.HandleControllerAspect -59 - 请求信息===> className:BasicErrorController,methodName:errorHtml,args:[org.apache.catalina.core.ApplicationHttpRequest@3529f16a, org.apache.catalina.connector.ResponseFacade@2152dbd3]
	 【a660f9b2-a669-4b56-b585-1b8369d4efcc_-55】 2018-12-17 15:00:36.943 [http-nio-8080-exec-1] INFO  com.lianshang.common.controller.aspect.	  HandleControllerAspect -97 - 响应信息===> className:BasicErrorController,methodName:errorHtml,入参:[org.apache.catalina.core.ApplicationHttpRequest@3529f16a, org.apache.catalina.connector.ResponseFacade@2152dbd3], 返回结果:null, 耗时:136


    服务端日志:
    	【a660f9b2-a669-4b56-b585-1b8369d4efcc_-55】 2018-12-17 15:00:36.809 [http-nio-8080-exec-1] INFO  com.lianshang.common.controller.aspect.HandleControllerAspect -59 - 请求信息===> className:BasicErrorController,methodName:errorHtml,args:[org.apache.catalina.core.ApplicationHttpRequest@3529f16a, org.apache.catalina.connector.ResponseFacade@2152dbd3]
    	【a660f9b2-a669-4b56-b585-1b8369d4efcc_-55】 2018-12-17 15:00:36.943 [http-nio-8080-exec-1] INFO  com.lianshang.common.controller.aspect.HandleControllerAspect -97 - 响应信息===> className:BasicErrorController,methodName:errorHtml,入参:[org.apache.catalina.core.ApplicationHttpRequest@3529f16a, org.apache.catalina.connector.ResponseFacade@2152dbd3], 返回结果:null, 耗时:136
    	 日志虽然在不同平台,但是有着相同的前缀  【a660f9b2-a669-4b56-b585-1b8369d4efcc_-55】

