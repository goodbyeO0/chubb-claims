package com.chubb.claims.api;

public class ApiException extends RuntimeException {
  public final int status;
  public final String code;

  public ApiException(int status, String code) {
    super(code);
    this.status = status;
    this.code = code;
  }

  public static ApiException missing() {
    return new ApiException(404, "NOT_FOUND");
  }

  public static ApiException invalid() {
    return new ApiException(400, "INVALID_INPUT");
  }

  public static ApiException conflict() {
    return new ApiException(409, "VERSION_CONFLICT");
  }
}
