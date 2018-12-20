package com.lianshang.cloud.server.config;
/**
 * 返回码
 * @author 孙龙云
 */
public enum ResponseCodeEnum {

	SUCCESS(200, "成功"),
	NEED_LOGIN(-101, "会话已过期,请重新登录"),
	NEED_SELECT_CLOTH_MACHIE(-120, "请选择验布机"),
	FAIL(-1, "失败");

	private Integer code;
	private String message;
	ResponseCodeEnum(Integer code, String message){
		this.code = code;
		this.message = message;
	}
	public Integer code(){
		return this.code;
	}
	public String message(){
		return this.message;
	}
}
