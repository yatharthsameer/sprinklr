package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.example.demo.websocket.TwilioWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final TwilioWebSocketHandler twilioWebSocketHandler;

    public WebSocketConfig(TwilioWebSocketHandler twilioWebSocketHandler) {
        this.twilioWebSocketHandler = twilioWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(twilioWebSocketHandler, "/ws").setAllowedOrigins("*");
    }
}
