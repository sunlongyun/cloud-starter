package com.lianshang.cloud.server.config;

import lombok.Data;

import java.io.Serializable;

/**
 * 返回值
 * @author 孙龙云
 */
@Data
public class Response implements Serializable{
	private static final long serialVersionUID = -7096601129585145021L;
	private Integer code;
	private String message;
	private Object data;

	private Response(Integer code, String message, Object data){
		this.code = code;
		this.message = message;
		this.data = data;
	}
	public static Response success(){
		return success(ResponseCodeEnum.SUCCESS.message ());
	}
	public static Response success(String msg){
		Response res = new Response(ResponseCodeEnum.SUCCESS.code(), msg, null);
		return res;
	}
	public static Response success(Object data){
		Response res = new Response(ResponseCodeEnum.SUCCESS.code(), ResponseCodeEnum.SUCCESS.message(), data);
		return res;
	}
	public static Response fail(Integer code, String msg){
		Response res = new Response(code, msg, null);
		return res;
	}

	public static Response get(ResponseCodeEnum responseCodeEnum){
		Response res = new Response(ResponseCodeEnum.NEED_LOGIN.code (), ResponseCodeEnum.NEED_LOGIN.message (), null);
		return res;
	}
	public static Response fail(String msg){
		Response res = new Response(ResponseCodeEnum.FAIL.code(), msg, null);
		return res;
	}
	public static Response fail(){
		return fail(ResponseCodeEnum.FAIL.message());
	}
	
}
