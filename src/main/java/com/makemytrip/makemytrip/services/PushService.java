package com.makemytrip.makemytrip.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.makemytrip.makemytrip.exceptions.ApiException;
import com.makemytrip.makemytrip.models.AppSetting;
import com.makemytrip.makemytrip.models.PushSubscription;
import com.makemytrip.makemytrip.repositories.AppSettingRepository;
import com.makemytrip.makemytrip.repositories.PushSubscriptionRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Browser push notifications (Web Push with VAPID). The browser registers a subscription (see PushController); every
 * notification that is created for a user is also pushed to all of the user's browsers, so it shows up even when the
 * site is closed. The VAPID key pair comes from the environment (VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY) or is
 * generated once and kept in the database.
 */
@Service
public class PushService {
    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    @Value("${app.push.public-key:}")
    private String configuredPublicKey;
    @Value("${app.push.private-key:}")
    private String configuredPrivateKey;
    @Value("${app.push.subject:mailto:admin@makemytour.com}")
    private String subject;
    /** Only for local tests: lets a subscription point to http://localhost. */
    @Value("${app.push.allow-insecure-endpoints:false}")
    private boolean allowInsecure;

    @Autowired
    private PushSubscriptionRepository subscriptionRepository;
    @Autowired
    private AppSettingRepository settingRepository;

    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "web-push");
        t.setDaemon(true);
        return t;
    });
    private nl.martijndwars.webpush.PushService client;
    private String publicKey;

    @PostConstruct
    void init() {
        try {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }
            String pub = configuredPublicKey;
            String priv = configuredPrivateKey;
            if (pub == null || pub.isBlank() || priv == null || priv.isBlank()) {
                AppSetting a = settingRepository.findById("vapid.public").orElse(null);
                AppSetting b = settingRepository.findById("vapid.private").orElse(null);
                if (a != null && b != null) {
                    pub = a.getValue();
                    priv = b.getValue();
                } else {
                    String[] pair = generateKeys();
                    pub = pair[0];
                    priv = pair[1];
                    settingRepository.save(new AppSetting("vapid.public", pub));
                    settingRepository.save(new AppSetting("vapid.private", priv));
                    log.info("Generated new push (VAPID) keys");
                }
            }
            this.publicKey = pub;
            this.client = new nl.martijndwars.webpush.PushService(pub, priv, subject);
        } catch (Exception e) {
            log.warn("Push notifications are switched off: {}", e.toString());
        }
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }

    public boolean enabled() {
        return client != null;
    }

    public String publicKey() {
        return publicKey;
    }

    private static String[] generateKeys() throws Exception {
        ECParameterSpec spec = ECNamedCurveTable.getParameterSpec("prime256v1");
        KeyPairGenerator generator = KeyPairGenerator.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME);
        generator.initialize(spec);
        KeyPair pair = generator.generateKeyPair();
        byte[] publicBytes = ((ECPublicKey) pair.getPublic()).getQ().getEncoded(false);
        BigInteger d = ((ECPrivateKey) pair.getPrivate()).getD();
        byte[] raw = d.toByteArray();
        byte[] privateBytes = new byte[32];
        if (raw.length > 32) {
            System.arraycopy(raw, raw.length - 32, privateBytes, 0, 32);
        } else {
            System.arraycopy(raw, 0, privateBytes, 32 - raw.length, raw.length);
        }
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        return new String[]{enc.encodeToString(publicBytes), enc.encodeToString(privateBytes)};
    }

    // ------------------------------------------------------------------ subscriptions

    public PushSubscription subscribe(String userId, String endpoint, String p256dh, String auth) {
        if (endpoint == null || p256dh == null || auth == null || endpoint.isBlank() || p256dh.isBlank() || auth.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The push subscription is incomplete");
        }
        checkEndpoint(endpoint);
        PushSubscription s = subscriptionRepository.findByEndpoint(endpoint).orElseGet(PushSubscription::new);
        s.setUserId(userId);
        s.setEndpoint(endpoint);
        s.setP256dh(p256dh);
        s.setAuth(auth);
        s.setCreatedAt(Instant.now());
        return subscriptionRepository.save(s);
    }

    public void unsubscribe(String userId, String endpoint) {
        subscriptionRepository.findByEndpoint(endpoint).ifPresent(s -> {
            if (userId.equals(s.getUserId())) {
                subscriptionRepository.delete(s);
            }
        });
    }

    public long count(String userId) {
        return subscriptionRepository.findByUserId(userId).size();
    }

    /** The server only talks to push services on the public internet over https (no internal addresses). */
    private void checkEndpoint(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            boolean https = "https".equalsIgnoreCase(uri.getScheme());
            if (host == null || (!https && !allowInsecure)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "The push endpoint must be an https address");
            }
            if (!allowInsecure) {
                InetAddress address;
                try {
                    address = InetAddress.getByName(host);
                } catch (java.net.UnknownHostException e) {
                    return;     // cannot be reached from here anyway; the push service address is checked again when sending fails
                }
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "The push endpoint is not allowed");
                }
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The push endpoint is not valid");
        }
    }

    // ------------------------------------------------------------------ sending

    /** Sends the message to every browser of the user. Never blocks and never throws. */
    public void sendToUser(String userId, String title, String body, String url, String tag) {
        if (client == null || userId == null) {
            return;
        }
        List<PushSubscription> targets = subscriptionRepository.findByUserId(userId);
        if (targets.isEmpty()) {
            return;
        }
        final String payload;
        try {
            payload = mapper.writeValueAsString(Map.of(
                    "title", title == null ? "MakeMyTour" : title,
                    "body", body == null ? "" : body,
                    "url", url == null || url.isBlank() ? "/" : url,
                    "tag", tag == null ? "mmt" : tag));
        } catch (Exception e) {
            return;
        }
        for (PushSubscription s : targets) {
            executor.submit(() -> deliver(s, payload));
        }
    }

    private void deliver(PushSubscription s, String payload) {
        try {
            Notification n = new Notification(s.getEndpoint(), s.getP256dh(), s.getAuth(), payload.getBytes(java.nio.charset.StandardCharsets.UTF_8), 3600);
            HttpResponse response = client.send(n, Encoding.AES128GCM);
            int status = response.getStatusLine().getStatusCode();
            if (status == 404 || status == 410) {
                subscriptionRepository.deleteByEndpoint(s.getEndpoint());     // the browser removed the subscription
            } else if (status >= 300) {
                log.debug("Push service answered {} for a subscription of user {}", status, s.getUserId());
            }
        } catch (Exception e) {
            log.debug("Could not push to {}: {}", s.getUserId(), e.toString());
        }
    }
}
