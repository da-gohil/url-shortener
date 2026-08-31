package com.darshangohil.urlshortener.web.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HealthCheckController {

    @GetMapping("/ping")
    public String ping(){
        return "URL Shortener Service is up and running";
    }
}
