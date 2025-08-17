package com.oss.mcpagent.controller;

import com.oss.mcpagent.service.WolframAlphaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/mcp")
public class AgentController {

    @Autowired
    private WolframAlphaService wolframService;

    @PostMapping("/query")
    public Map<String, String> handleMessage(@RequestBody Map<String, String> body) {
        String question = body.get("message");
        String result = wolframService.queryWolfram(question);
        return Map.of("response", result);
    }
}

