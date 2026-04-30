package ru.normacontrol.infrastructure.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import ru.normacontrol.infrastructure.security.JwtTokenProvider;

import java.util.Collections;
import java.util.UUID;

/**
 * Перехватчик для аутентификации WebSocket (STOMP) соединений по JWT.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                if (jwtTokenProvider.validateToken(token)) {
                    UUID userId = UUID.fromString(jwtTokenProvider.getUserIdFromToken(token));
                    
                    var authentication = new UsernamePasswordAuthenticationToken(
                            userId, null, Collections.emptyList());
                    accessor.setUser(authentication);
                    log.debug("WebSocket соединение установлено для пользователя: {}", userId);
                } else {
                    log.warn("Невалидный JWT токен при подключении WebSocket");
                }
            } else {
                log.warn("Отсутствует заголовок Authorization при подключении WebSocket");
            }
        }
        return message;
    }
}
