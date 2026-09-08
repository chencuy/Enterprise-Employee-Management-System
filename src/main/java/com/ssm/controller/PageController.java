package com.ssm.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {
    @GetMapping({"/", "/login", "/dashboard"})
    public String login() {
        return "login";
    }
}
