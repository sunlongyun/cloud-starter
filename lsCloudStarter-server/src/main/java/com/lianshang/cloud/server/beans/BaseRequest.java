package com.lianshang.cloud.server.beans;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;

import lombok.Data;

@Data
public class BaseRequest implements Serializable {
  private static final long serialVersionUID = -3185396167770547346L;
  private String interfaceName;
  private String methodName;
  private List<String> paramTypeNames;
  private List<LinkedHashMap> params;
}
