package com.ssm.controller;

import com.ssm.dto.ApiResponse;
import com.ssm.service.CaptchaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/captcha")
public class CaptchaController {
    private final CaptchaService captchaService;

    public CaptchaController(CaptchaService captchaService) {
        this.captchaService = captchaService;
    }

    @GetMapping("/check-in")
    public ApiResponse<Map<String, Object>> checkInCaptcha() {
        CaptchaService.CaptchaChallenge challenge = captchaService.generateChallenge();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", challenge.token);
        data.put("image", challenge.image);
        return ApiResponse.ok(data);
    }
}
