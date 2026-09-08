package com.ssm.dto;

public class ApiResponse<T> {
    public boolean success;
    public String message;
    public T data;

    private ApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "操作成功", data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, "操作成功", null);
    }

    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, message == null ? "请求失败" : message, null);
    }
}
