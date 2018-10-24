package com.lianshang.cloud.server.beans;

import java.io.Serializable;
import lombok.Data;

@Data
public class BaseRequest implements Serializable {
  private static final long serialVersionUID = -3185396167770547346L;
  private String interfaceName;
  private String methodName;
  private Object[] params;
}
