package com.lianshang.cloud.server.beans;

import com.lianshang.cloud.server.enums.ResponseCodeEnum;
import lombok.Data;

import java.io.Serializable;

/**
 * 返回值
 *
 * @author 孙龙云
 */
@Data
public class LsCloudResponse implements Serializable {

  private static final long serialVersionUID = -7096601129585145021L;
  private String code = null;
  private String msg = null;
  private Object data = null;

  private LsCloudResponse(String code, String msg, Object data) {
    this.code = code;
    this.msg = msg;
    this.data = data;
  }


  public static LsCloudResponse success() {
    return success(ResponseCodeEnum.SUCCESS.msg());
  }

  public static LsCloudResponse success(String msg) {
    LsCloudResponse res = new LsCloudResponse(ResponseCodeEnum.SUCCESS.code(), msg, null);
    return res;
  }


  public static LsCloudResponse success(Object data) {
    LsCloudResponse res = new LsCloudResponse(ResponseCodeEnum.SUCCESS.code(), ResponseCodeEnum.SUCCESS.msg(), data);
    return res;
  }


  public static LsCloudResponse fail(String msg) {
    LsCloudResponse res = new LsCloudResponse(ResponseCodeEnum.FAIL.code(), msg, null);
    return res;
  }



  public LsCloudResponse() {

  }
}
