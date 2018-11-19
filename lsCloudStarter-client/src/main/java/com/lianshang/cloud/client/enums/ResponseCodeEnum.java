package com.lianshang.cloud.client.enums;
/**
 * 返回码
 * @author 孙龙云
 *
 */
public enum ResponseCodeEnum {
	SUCCESS("200", "成功"),
	FAIL("9999", "失败");
	private String code;
	private String msg;
	ResponseCodeEnum(String code, String msg){
		this.code = code;
		this.msg = msg;
	}
	public String code(){
		return this.code;
	}
	public String msg(){
		return this.msg;
	}
}
