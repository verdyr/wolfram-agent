package com.oss.mcpagent.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;

@Service
public class WolframAlphaService {

    @Value("${wolframalpha.appid}")
    private String appId;

    private final WebClient webClient = WebClient.create("http://api.wolframalpha.com");

    public String queryWolfram(String input) {
        try {
            String xml = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/query")
                            .queryParam("input", input)
                            .queryParam("appid", appId)
                            .queryParam("format", "plaintext")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (xml == null) return "No response from Wolfram Alpha.";

            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes()));

            NodeList podList = doc.getElementsByTagName("pod");
            for (int i = 0; i < podList.getLength(); i++) {
                Element pod = (Element) podList.item(i);
                if ("Result".equalsIgnoreCase(pod.getAttribute("title"))) {
                    NodeList subpods = pod.getElementsByTagName("plaintext");
                    if (subpods.getLength() > 0) {
                        return subpods.item(0).getTextContent();
                    }
                }
            }

            return "Could not extract result.";

        } catch (Exception e) {
            return "Error processing Wolfram Alpha query: " + e.getMessage();
        }
    }
}
