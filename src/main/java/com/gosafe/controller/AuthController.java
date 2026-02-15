package com.gosafe.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gosafe.dto.*;
import com.gosafe.entity.*;
import com.gosafe.repository.*;
import com.gosafe.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository         userRepo;
    private final RouteHistoryRepository historyRepo;
    private final SavedRouteRepository   savedRepo;
    private final JwtUtil                jwtUtil;
    private final BCryptPasswordEncoder  bcrypt = new BCryptPasswordEncoder(12);
    private final ObjectMapper           mapper = new ObjectMapper();

    @Value("${gosafe.upload.dir}")
    private String uploadDir;

    public AuthController(UserRepository userRepo,
                          RouteHistoryRepository historyRepo,
                          SavedRouteRepository savedRepo,
                          JwtUtil jwtUtil) {
        this.userRepo    = userRepo;
        this.historyRepo = historyRepo;
        this.savedRepo   = savedRepo;
        this.jwtUtil     = jwtUtil;
    }

    // ── Helper: get userId from request, or 401 ───────────────────────────────
    private Long requireAuth(HttpServletRequest req) {
        Object uid = req.getAttribute("userId");
        if (uid == null) throw new UnauthorizedException("No token provided.");
        return (Long) uid;
    }

    // ── POST /api/auth/signup ─────────────────────────────────────────────────
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest body) {
        String email = body.getEmail().trim().toLowerCase();
        if (userRepo.existsByEmail(email))
            return ResponseEntity.status(409).body(Map.of("error", "An account with that email already exists."));

        User user = new User();
        user.setName(body.getName().trim());
        user.setEmail(email);
        user.setPasswordHash(bcrypt.encode(body.getPassword()));
        user = userRepo.save(user);

        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getName());
        return ResponseEntity.status(201).body(Map.of(
            "token", token,
            "user",  safeUser(user)
        ));
    }

    // ── POST /api/auth/login ──────────────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest body) {
        String email = body.getEmail().trim().toLowerCase();
        Optional<User> opt = userRepo.findByEmail(email);

        if (opt.isEmpty() || !bcrypt.matches(body.getPassword(), opt.get().getPasswordHash()))
            return ResponseEntity.status(401).body(Map.of("error", "Invalid email or password."));

        User user  = opt.get();
        String tok = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getName());
        return ResponseEntity.ok(Map.of("token", tok, "user", safeUser(user)));
    }

    // ── GET /api/auth/me ──────────────────────────────────────────────────────
    @GetMapping("/me")
    public ResponseEntity<?> getMe(HttpServletRequest req) {
        Long uid = requireAuth(req);
        return userRepo.findById(uid)
            .map(u -> ResponseEntity.ok(Map.of("user", safeUser(u))))
            .orElse(ResponseEntity.status(404).body(null));
    }

    // ── PATCH /api/auth/profile ───────────────────────────────────────────────
    @PatchMapping("/profile")
    public ResponseEntity<?> updateProfile(
            HttpServletRequest req,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) MultipartFile avatar) throws IOException {

        Long uid  = requireAuth(req);
        User user = userRepo.findById(uid)
            .orElseThrow(() -> new UnauthorizedException("User not found."));

        if (name  != null && !name.isBlank())  user.setName(name.trim());
        if (city  != null) user.setCity(city.trim());
        if (phone != null) user.setPhone(phone.trim());

        if (avatar != null && !avatar.isEmpty()) {
            String mime = avatar.getContentType();
            if (mime == null || !mime.startsWith("image/"))
                return ResponseEntity.badRequest().body(Map.of("error", "Only image files allowed."));
            if (avatar.getSize() > 3 * 1024 * 1024)
                return ResponseEntity.badRequest().body(Map.of("error", "File too large (max 3MB)."));

            String ext      = Objects.requireNonNull(avatar.getOriginalFilename()).replaceAll(".*\\.", ".");
            String filename = "avatar_" + System.currentTimeMillis() + ext;
            Path dir        = Paths.get(uploadDir).toAbsolutePath();
            Files.createDirectories(dir);
            Files.copy(avatar.getInputStream(), dir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            user.setAvatarUrl("/uploads/" + filename);
        }

        user = userRepo.save(user);
        return ResponseEntity.ok(Map.of("user", safeUser(user)));
    }

    // ── POST /api/auth/change-password ────────────────────────────────────────
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(HttpServletRequest req,
                                            @Valid @RequestBody ChangePasswordRequest body) {
        Long uid  = requireAuth(req);
        User user = userRepo.findById(uid)
            .orElseThrow(() -> new UnauthorizedException("User not found."));

        if (!bcrypt.matches(body.getCurrentPassword(), user.getPasswordHash()))
            return ResponseEntity.status(401).body(Map.of("error", "Current password is incorrect."));

        user.setPasswordHash(bcrypt.encode(body.getNewPassword()));
        userRepo.save(user);
        return ResponseEntity.ok(Map.of("message", "Password updated successfully."));
    }

    // ── GET /api/auth/history ─────────────────────────────────────────────────
    @GetMapping("/history")
    public ResponseEntity<?> getHistory(HttpServletRequest req) {
        Long uid = requireAuth(req);
        return ResponseEntity.ok(Map.of("history", historyRepo.findTop20ByUserIdOrderBySearchedAtDesc(uid)));
    }

    // ── POST /api/auth/saved-routes ───────────────────────────────────────────
    @PostMapping("/saved-routes")
    public ResponseEntity<?> saveRoute(HttpServletRequest req,
                                       @RequestBody SaveRouteRequest body) throws Exception {
        Long uid = requireAuth(req);
        SavedRoute sr = new SavedRoute();
        sr.setUserId(uid);
        sr.setOrigin(body.getOrigin());
        sr.setDestination(body.getDestination());
        sr.setRouteName(body.getRoute_name());
        sr.setLabel(body.getLabel());
        if (body.getRoute_data() != null)
            sr.setRouteData(mapper.writeValueAsString(body.getRoute_data()));
        savedRepo.save(sr);
        return ResponseEntity.status(201).body(Map.of("message", "Route saved."));
    }

    // ── GET /api/auth/saved-routes ────────────────────────────────────────────
    @GetMapping("/saved-routes")
    public ResponseEntity<?> getSavedRoutes(HttpServletRequest req) {
        Long uid = requireAuth(req);
        return ResponseEntity.ok(Map.of("routes", savedRepo.findByUserIdOrderBySavedAtDesc(uid)));
    }

    // ── DELETE /api/auth/saved-routes/{id} ────────────────────────────────────
    @DeleteMapping("/saved-routes/{id}")
    public ResponseEntity<?> deleteSavedRoute(HttpServletRequest req, @PathVariable Long id) {
        Long uid = requireAuth(req);
        savedRepo.findByIdAndUserId(id, uid).ifPresent(savedRepo::delete);
        return ResponseEntity.ok(Map.of("message", "Route deleted."));
    }

    // ── Helper: strip passwordHash from User ─────────────────────────────────
    private Map<String, Object> safeUser(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",         u.getId());
        m.put("name",       u.getName());
        m.put("email",      u.getEmail());
        m.put("avatar_url", u.getAvatarUrl());
        m.put("city",       u.getCity());
        m.put("phone",      u.getPhone());
        m.put("role",       u.getRole());
        m.put("created_at", u.getCreatedAt());
        return m;
    }

    // ── Inner exception class for 401 ────────────────────────────────────────
    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String msg) { super(msg); }
    }
}
