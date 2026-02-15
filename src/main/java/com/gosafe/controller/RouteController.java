package com.gosafe.controller;

import com.gosafe.dto.RouteSearchRequest;
import com.gosafe.entity.RouteHistory;
import com.gosafe.repository.RouteHistoryRepository;
import com.gosafe.service.RouteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
public class RouteController {

    private final RouteService           routeService;
    private final RouteHistoryRepository historyRepo;

    public RouteController(RouteService routeService, RouteHistoryRepository historyRepo) {
        this.routeService = routeService;
        this.historyRepo  = historyRepo;
    }

    // ── GET /api/stations?q=...&lat=...&lng=... ───────────────────────────────
    // (equivalent of GET /api/stations in stations.js)
    @GetMapping("/api/stations")
    public ResponseEntity<?> stations(
            @RequestParam String q,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {

        if (q == null || q.trim().length() < 2)
            return ResponseEntity.ok(Map.of("stations", List.of()));

        try {
            List<Map<String, Object>> results;
            if (lat != null && lng != null) {
                results = routeService.autocompleteNearby(q.trim(),
                          Map.of("lat", lat, "lng", lng));
            } else {
                results = routeService.autocomplete(q.trim());
            }
            return ResponseEntity.ok(Map.of("stations", results));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("stations", List.of()));
        }
    }

    // ── GET /api/routes/autocomplete?q=... ───────────────────────────────────
    @GetMapping("/api/routes/autocomplete")
    public ResponseEntity<?> autocomplete(@RequestParam(required = false) String q) {
        if (q == null || q.trim().length() < 2)
            return ResponseEntity.ok(Map.of("suggestions", List.of()));
        try {
            return ResponseEntity.ok(Map.of("suggestions", routeService.autocomplete(q.trim())));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("suggestions", List.of()));
        }
    }

    // ── POST /api/routes/search ───────────────────────────────────────────────
    @PostMapping("/api/routes/search")
    public ResponseEntity<?> searchRoutes(HttpServletRequest req,
                                          @Valid @RequestBody RouteSearchRequest body) {
        String origin = body.getOrigin().trim();
        String dest   = body.getDestination().trim();

        if (origin.equalsIgnoreCase(dest))
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Origin and destination cannot be the same."));

        try {
            List<Map<String, Object>> routes = routeService.generateRoutes(origin, dest);

            // Save to history if authenticated
            Object uid = req.getAttribute("userId");
            if (uid != null && !routes.isEmpty()) {
                Map<String, Object> best = routes.get(0);
                RouteHistory h = new RouteHistory();
                h.setUserId((Long) uid);
                h.setOrigin(origin);
                h.setDestination(dest);
                h.setRouteName((String) best.get("name"));
                h.setDistance((String) best.get("distance"));
                h.setDuration((String) best.get("duration"));
                h.setSafetyScore((Integer) best.get("safetyScore"));
                historyRepo.save(h);
            }

            return ResponseEntity.ok(Map.of(
                "success",     true,
                "origin",      origin,
                "destination", dest,
                "routes",      routes
            ));
        } catch (Exception e) {
            String msg = e.getMessage();
            boolean userFacing = msg != null &&
                (msg.contains("not find") || msg.contains("No drivable"));
            return ResponseEntity.status(500)
                .body(Map.of("error", userFacing ? msg : "Failed to generate routes."));
        }
    }

    // ── POST /api/routes/finalize ─────────────────────────────────────────────
    @PostMapping("/api/routes/finalize")
    public ResponseEntity<?> finalize(@RequestBody Map<String, String> body) {
        String routeId = body.get("routeId");
        if (routeId == null)
            return ResponseEntity.badRequest().body(Map.of("error", "routeId required."));
        return ResponseEntity.ok(Map.of("success", true, "routeId", routeId, "status", "finalized"));
    }

    // ── GET /api/routes/live/{routeId} ────────────────────────────────────────
    @GetMapping("/api/routes/live/{routeId}")
    public ResponseEntity<?> live(@PathVariable String routeId) {
        return ResponseEntity.ok(Map.of(
            "routeId",     routeId,
            "status",      "active",
            "lastUpdated", new Date().toString()
        ));
    }

    // ── GET /api/health ───────────────────────────────────────────────────────
    @GetMapping("/api/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
