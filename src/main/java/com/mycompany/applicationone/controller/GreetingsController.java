package com.mycompany.applicationone.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/** Adaptation du controleur du tutoriel : compteur partage dans Redis. */
@RestController
public class GreetingsController {
    private final StringRedisTemplate redis;
    private final String containerId;

    public GreetingsController(StringRedisTemplate redis,
            @Value("${HOSTNAME:local}") String containerId) {
        this.redis = redis;
        this.containerId = containerId;
    }

    private long increment() {
        // INCR est atomique, meme avec trois instances web.
        Long hits = redis.opsForValue().increment("hits");
        if (hits == null) throw new IllegalStateException("Redis: resultat absent");
        return hits;
    }

    @GetMapping(value = {"/", "/api/v1/welcome"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> welcome() {
        long hits = increment();
        String html = """
            <!doctype html><html lang="fr"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Visit-Counter | TP1 DevOps</title>
            <style>
            body{font:18px system-ui,sans-serif;background:#f1f5f9;color:#122c44;
              margin:0;padding:9vh 7vw}main{max-width:850px;margin:auto;background:white;
              border:1px solid #d9e2eb;border-radius:18px;padding:45px}
            small{letter-spacing:2px;color:#52748d}h1{font-size:42px;margin-bottom:10px}
            .count{font-size:80px;font-weight:700;color:#007c91;margin:22px 0}
            code{background:#e8f1f4;padding:6px 12px;border-radius:5px}
            a{display:inline-block;margin:25px 0;color:white;background:#007c91;
              padding:12px 22px;border-radius:8px;text-decoration:none}
            footer{border-top:1px solid #e1e7ee;padding-top:20px;font-size:14px;color:#587083}
            </style></head><body><main><small>TP1 GRP1 / LABORATOIRE DEVOPS</small>
            <h1>Visit-Counter</h1><p>Spring Boot + Redis + Docker Compose</p>
            <div class="count">%d</div>
            <p>Bonjour ! Cette page a été vue %d fois. Je suis le conteneur <code>%s</code>.</p>
            <a href="/">Nouvelle visite</a>
            <footer>Ali Dridi · Rayenne Abid · Aymen Chahed · Bochra Karoui</footer>
            </main></body></html>
            """.formatted(hits, hits, HtmlUtils.htmlEscape(containerId));
        return ResponseEntity.ok().header("Cache-Control", "no-store")
            .header("X-Container-Id", containerId).body(html);
    }

    @GetMapping("/api/visits")
    public ResponseEntity<Map<String, Object>> visits() {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
            .body(Map.of("hits", increment(), "container", containerId));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        // La sonde de sante ne compte pas comme une visite.
        redis.hasKey("hits");
        return ResponseEntity.ok("UP");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<String> redisUnavailable(DataAccessException error) {
        return ResponseEntity.status(503).header("Retry-After", "3")
            .body("Redis est temporairement indisponible. Réessayez dans quelques secondes.");
    }
}
