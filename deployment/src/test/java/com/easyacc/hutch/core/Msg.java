package com.easyacc.hutch.core;

/** Created by IntelliJ IDEA. User: wyatt Date: 2022/3/15 Time: 19:16 */
public class Msg {
  String body;
  int code;
  boolean isSuccess;

  public Msg(String body, int code, boolean isSuccess) {
    this.body = body;
    this.code = code;
    this.isSuccess = isSuccess;
  }

  public Msg() {}

  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    this.body = body;
  }

  public int getCode() {
    return code;
  }

  public void setCode(int code) {
    this.code = code;
  }

  public boolean getIsSuccess() {
    return isSuccess;
  }

  public void setIsSuccess(boolean success) {
    isSuccess = success;
  }
}
