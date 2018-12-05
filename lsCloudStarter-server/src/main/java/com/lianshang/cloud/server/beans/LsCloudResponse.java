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
  private String code = ResponseCodeEnum.SUCCESS.code();
  private String msg = ResponseCodeEnum.SUCCESS.msg();
  /**
   * 返回值类型名称
   */
  private String returnTypeName;

  private Object data = new Serializable() {
  };

  private LsCloudResponse(String returnTypeName, String code, String msg, Object data) {
    this.returnTypeName = returnTypeName;
    this.code = code;
    this.msg = msg;
    this.data = data;
  }

  public static LsCloudResponse success(String returnTypeName) {
    return success(returnTypeName, ResponseCodeEnum.SUCCESS.msg());
  }

  public static LsCloudResponse success(String returnTypeName, String msg) {
    LsCloudResponse res = new LsCloudResponse(returnTypeName, ResponseCodeEnum.SUCCESS.code(), msg, null);
    return res;
  }

  public static LsCloudResponse success(String returnTypeName, Object data) {
    LsCloudResponse res = new LsCloudResponse(returnTypeName, ResponseCodeEnum.SUCCESS.code(), ResponseCodeEnum.SUCCESS.msg(), data);
    return res;
  }

  public static LsCloudResponse fail(String returnTypeName, String msg) {
    LsCloudResponse res = new LsCloudResponse(returnTypeName, ResponseCodeEnum.FAIL.code(), msg, null);
    return res;
  }

  public static LsCloudResponse fail(String returnTypeName) {
    return fail (returnTypeName, ResponseCodeEnum.FAIL.msg ());
  }

  public LsCloudResponse() {

  }
}
