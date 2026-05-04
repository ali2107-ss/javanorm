package ru.normacontrol.presentation.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DocumentationController {

    @GetMapping("/api/swagger-ui")
    public String redirectOldSwaggerUrl() {
        return "redirect:/api/docs";
    }
}
