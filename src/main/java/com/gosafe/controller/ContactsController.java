package com.gosafe.controller;

import com.gosafe.dto.AddContactRequest;
import com.gosafe.entity.EmergencyContact;
import com.gosafe.repository.EmergencyContactRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/contacts")
public class ContactsController {

    private final EmergencyContactRepository repo;

    public ContactsController(EmergencyContactRepository repo) {
        this.repo = repo;
    }

    private Long requireAuth(HttpServletRequest req) {
        Object uid = req.getAttribute("userId");
        if (uid == null)
            throw new AuthController.UnauthorizedException("No token provided.");
        return (Long) uid;
    }

    // ── GET /api/contacts ─────────────────────────────────────────────────────
    @GetMapping
    public ResponseEntity<?> getContacts(HttpServletRequest req) {
        Long uid = requireAuth(req);
        return ResponseEntity.ok(Map.of("contacts", repo.findByUserIdOrderByIdAsc(uid)));
    }

    // ── POST /api/contacts ────────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<?> addContact(HttpServletRequest req,
                                        @Valid @RequestBody AddContactRequest body) {
        Long uid = requireAuth(req);

        if (repo.countByUserId(uid) >= 5)
            return ResponseEntity.badRequest().body(Map.of("error", "Maximum 5 emergency contacts allowed."));

        // Sanitise phone: strip spaces, dashes, parentheses
        String cleanPhone = body.getPhone().trim().replaceAll("[\\s\\-()]", "");

        EmergencyContact c = new EmergencyContact();
        c.setUserId(uid);
        c.setName(body.getName().trim());
        c.setPhone(cleanPhone);
        c.setRelation(body.getRelation() != null ? body.getRelation().trim() : null);
        c = repo.save(c);

        return ResponseEntity.status(201).body(Map.of("contact", c));
    }

    // ── DELETE /api/contacts/{id} ─────────────────────────────────────────────
    @Transactional
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteContact(HttpServletRequest req, @PathVariable Long id) {
        Long uid = requireAuth(req);
        repo.deleteByIdAndUserId(id, uid);
        return ResponseEntity.ok(Map.of("message", "Contact removed."));
    }
}
