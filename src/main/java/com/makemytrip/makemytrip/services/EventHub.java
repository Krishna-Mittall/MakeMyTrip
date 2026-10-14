package com.makemytrip.makemytrip.services;

import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tiny Server-Sent-Events hub. The browser opens one EventSource connection and
 * receives live seat, price, flight-status and notification events through it.
 */
@Service
public class EventHub {

    private static class Client {
        private final String userId;
        private final SseEmitter emitter;

        private Client(String userId, SseEmitter emitter) {
            this.userId = userId;
            this.emitter = emitter;
        }
    }

    private final List<Client> clients = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe(String userId) {
        SseEmitter emitter = new SseEmitter(0L);
        Client client = new Client(userId, emitter);
        clients.add(client);
        emitter.onCompletion(() -> clients.remove(client));
        emitter.onTimeout(() -> clients.remove(client));
        emitter.onError(e -> clients.remove(client));
        try {
            emitter.send(SseEmitter.event().name("CONNECTED").data("ok"));
        } catch (Exception e) {
            clients.remove(client);
        }
        return emitter;
    }

    /** Sends an event to everybody who is connected. */
    public void publish(String type, Object payload) {
        send(null, type, payload);
    }

    /** Sends an event only to the connections opened by one user. */
    public void publishToUser(String userId, String type, Object payload) {
        if (userId == null) {
            return;
        }
        send(userId, type, payload);
    }

    private void send(String userId, String type, Object payload) {
        for (Client client : clients) {
            if (userId != null && !userId.equals(client.userId)) {
                continue;
            }
            try {
                synchronized (client.emitter) {
                    client.emitter.send(SseEmitter.event().name(type).data(payload, MediaType.APPLICATION_JSON));
                }
            } catch (Exception e) {
                clients.remove(client);
            }
        }
    }

    @Scheduled(fixedRate = 20000)
    public void heartbeat() {
        for (Client client : clients) {
            try {
                synchronized (client.emitter) {
                    client.emitter.send(SseEmitter.event().comment("keep-alive"));
                }
            } catch (Exception e) {
                clients.remove(client);
            }
        }
    }

    public int connectedClients() {
        return clients.size();
    }
}
