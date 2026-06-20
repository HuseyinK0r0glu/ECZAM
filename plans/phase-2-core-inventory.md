# Phase 2 — Core Inventory

> **Goal:** the global medication catalog and the user's personal inventory, with manual
> entry, barcode scanning (+ OpenFDA fallback), low-stock and expiry indicators, and a
> medication detail page with a searchable leaflet viewer.
>
> **Realizes:** EP-02 · FR-010…026, FR-080…083 · UC-002, UC-003.
> **Prerequisites:** [phase-1-foundation.md](phase-1-foundation.md).
> **Exit criteria:** add a medication (manual + barcode), view/edit/delete inventory with
> badges, browse a leaflet.

---

## 1. Dependencies

- Backend: no new deps (uses Spring `RestClient`, included in Boot 3.2).
- Frontend: `npm i html5-qrcode` (camera scanning).

No new migrations — all tables exist from Phase 1.

---

## 2. Backend

### Medications (catalog)

#### `backend/src/main/java/com/eczam/medications/LeafletSections.java`

```java
package com.eczam.medications;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Structured leaflet content stored as JSONB (brief §6.2). */
public record LeafletSections(
        String dosage,
        @JsonProperty("side_effects") String sideEffects,
        String contraindications,
        String storage,
        String interactions,
        @JsonProperty("missed_dose") String missedDose) {}
```

#### `backend/src/main/java/com/eczam/medications/Medication.java`

```java
package com.eczam.medications;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "medications")
@Getter @Setter @NoArgsConstructor
public class Medication {

    @Id @GeneratedValue
    private UUID id;

    @Column(nullable = false) private String name;
    @Column(name = "generic_name") private String genericName;
    private String manufacturer;
    @Column(unique = true) private String barcode;
    private String form;
    private String strength;

    @Column(name = "leaflet_raw", columnDefinition = "text")
    private String leafletRaw;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "leaflet_sections", columnDefinition = "jsonb")
    private LeafletSections leafletSections;

    @Column(name = "vector_indexed", nullable = false)
    private boolean vectorIndexed = false;

    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
```

#### `backend/src/main/java/com/eczam/medications/MedicationRepository.java`

```java
package com.eczam.medications;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MedicationRepository extends JpaRepository<Medication, UUID> {
    Optional<Medication> findByBarcode(String barcode);

    @Query("""
           SELECT m FROM Medication m
           WHERE :q IS NULL OR LOWER(m.name) LIKE LOWER(CONCAT('%', :q, '%'))
              OR LOWER(m.genericName) LIKE LOWER(CONCAT('%', :q, '%'))
           ORDER BY m.name ASC
           """)
    Page<Medication> search(@Param("q") String q, Pageable pageable);
}
```

#### `backend/src/main/java/com/eczam/medications/dto/MedicationDtos.java`

```java
package com.eczam.medications.dto;

import com.eczam.medications.LeafletSections;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class MedicationDtos {

    public record MedicationView(
            String id, String name, String genericName, String manufacturer,
            String barcode, String form, String strength,
            boolean vectorIndexed) {}

    public record MedicationDetail(
            String id, String name, String genericName, String manufacturer,
            String barcode, String form, String strength,
            LeafletSections leafletSections, boolean vectorIndexed) {}

    public record CreateMedicationRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 255) String genericName,
            @Size(max = 255) String manufacturer,
            @Size(max = 100) String barcode,
            @Size(max = 50) String form,
            @Size(max = 50) String strength,
            String leafletRaw,
            LeafletSections leafletSections) {}

    public record LeafletSearchHit(String section, String snippet) {}

    public record LeafletSearchResult(List<LeafletSearchHit> hits) {}

    private MedicationDtos() {}
}
```

#### `backend/src/main/java/com/eczam/integrations/barcode/OpenFdaClient.java`

```java
package com.eczam.integrations.barcode;

import com.eczam.medications.LeafletSections;
import com.eczam.medications.Medication;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** OpenFDA drug-label fallback (brief §9.2). */
@Component
public class OpenFdaClient {

    private final RestClient http = RestClient.builder()
            .baseUrl("https://api.fda.gov").build();

    @SuppressWarnings("unchecked")
    public Optional<Medication> lookupByBarcode(String code) {
        try {
            Map<String, Object> body = http.get()
                    .uri(uri -> uri.path("/drug/label.json")
                            .queryParam("search", "openfda.upc_udi_di:" + code)
                            .queryParam("limit", 1).build())
                    .retrieve().body(Map.class);

            List<Map<String, Object>> results = body == null ? null : (List<Map<String, Object>>) body.get("results");
            if (results == null || results.isEmpty()) return Optional.empty();

            Map<String, Object> r = results.get(0);
            Map<String, Object> openfda = (Map<String, Object>) r.getOrDefault("openfda", Map.of());

            Medication m = new Medication();
            m.setName(firstOf(openfda.get("brand_name"), "Unknown"));
            m.setGenericName(firstOf(openfda.get("generic_name"), null));
            m.setManufacturer(firstOf(openfda.get("manufacturer_name"), null));
            m.setBarcode(code);
            m.setLeafletRaw(joinAll(r));
            m.setLeafletSections(new LeafletSections(
                    text(r.get("dosage_and_administration")),
                    text(r.get("adverse_reactions")),
                    text(r.get("contraindications")),
                    text(r.get("how_supplied_storage_and_handling")),
                    text(r.get("drug_interactions")),
                    text(r.get("dosage_and_administration"))));
            return Optional.of(m);
        } catch (Exception e) {
            return Optional.empty(); // treated as a miss → manual entry
        }
    }

    @SuppressWarnings("unchecked")
    private static String firstOf(Object v, String fallback) {
        if (v instanceof List<?> l && !l.isEmpty()) return String.valueOf(l.get(0));
        return fallback;
    }
    @SuppressWarnings("unchecked")
    private static String text(Object v) {
        if (v instanceof List<?> l && !l.isEmpty()) return String.valueOf(l.get(0));
        return v == null ? null : String.valueOf(v);
    }
    private static String joinAll(Map<String, Object> r) {
        StringBuilder sb = new StringBuilder();
        r.forEach((k, v) -> { if (v instanceof List<?> l && !l.isEmpty()) sb.append(k).append(": ").append(l.get(0)).append("\n\n"); });
        return sb.toString();
    }
}
```

#### `backend/src/main/java/com/eczam/medications/MedicationService.java`

```java
package com.eczam.medications;

import com.eczam.integrations.barcode.OpenFdaClient;
import com.eczam.medications.dto.MedicationDtos.*;
import com.eczam.shared.web.ApiException;
import com.eczam.shared.web.CursorCodec;
import com.eczam.shared.web.ErrorCode;
import com.eczam.shared.web.Meta;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class MedicationService {

    private final MedicationRepository repo;
    private final OpenFdaClient openFda;

    public MedicationService(MedicationRepository repo, OpenFdaClient openFda) {
        this.repo = repo;
        this.openFda = openFda;
    }

    @Transactional(readOnly = true)
    public List<MedicationView> search(String q, int limit) {
        return repo.search(q == null || q.isBlank() ? null : q, PageRequest.of(0, limit))
                .map(MedicationService::toView).getContent();
    }

    @Transactional(readOnly = true)
    public MedicationDetail get(UUID id) {
        return toDetail(load(id));
    }

    @Transactional
    public MedicationDetail create(CreateMedicationRequest req) {
        Medication m = new Medication();
        m.setName(req.name());
        m.setGenericName(req.genericName());
        m.setManufacturer(req.manufacturer());
        m.setBarcode(emptyToNull(req.barcode()));
        m.setForm(req.form());
        m.setStrength(req.strength());
        m.setLeafletRaw(req.leafletRaw());
        m.setLeafletSections(req.leafletSections());
        repo.save(m);
        // Phase 5: LeafletIndexer.ingest(m) triggered here.
        return toDetail(m);
    }

    /** Barcode lookup: local → OpenFDA (create + ingest) → 404. */
    @Transactional
    public MedicationDetail lookupByBarcode(String code) {
        return repo.findByBarcode(code)
                .map(MedicationService::toDetail)
                .orElseGet(() -> openFda.lookupByBarcode(code)
                        .map(m -> {
                            repo.save(m);
                            // Phase 5: LeafletIndexer.ingest(m) (async) triggered here.
                            return toDetail(m);
                        })
                        .orElseThrow(() -> new ApiException(
                                org.springframework.http.HttpStatus.NOT_FOUND,
                                ErrorCode.BARCODE_NOT_FOUND,
                                "Barcode not found; please add the medication manually")));
    }

    @Transactional(readOnly = true)
    public LeafletSearchResult searchLeaflet(UUID id, String q) {
        LeafletSections s = load(id).getLeafletSections();
        List<LeafletSearchHit> hits = new ArrayList<>();
        if (s != null && q != null && !q.isBlank()) {
            String needle = q.toLowerCase();
            addHit(hits, "dosage", s.dosage(), needle);
            addHit(hits, "side_effects", s.sideEffects(), needle);
            addHit(hits, "contraindications", s.contraindications(), needle);
            addHit(hits, "storage", s.storage(), needle);
            addHit(hits, "interactions", s.interactions(), needle);
            addHit(hits, "missed_dose", s.missedDose(), needle);
        }
        return new LeafletSearchResult(hits);
    }

    public Meta cursorMeta(int limit) { return new Meta(null, limit); }

    Medication load(UUID id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("Medication not found"));
    }

    private static void addHit(List<LeafletSearchHit> hits, String section, String text, String needle) {
        if (text == null) return;
        int idx = text.toLowerCase().indexOf(needle);
        if (idx >= 0) {
            int start = Math.max(0, idx - 60);
            int end = Math.min(text.length(), idx + needle.length() + 60);
            hits.add(new LeafletSearchHit(section, "…" + text.substring(start, end).trim() + "…"));
        }
    }

    static MedicationView toView(Medication m) {
        return new MedicationView(m.getId().toString(), m.getName(), m.getGenericName(),
                m.getManufacturer(), m.getBarcode(), m.getForm(), m.getStrength(), m.isVectorIndexed());
    }
    static MedicationDetail toDetail(Medication m) {
        return new MedicationDetail(m.getId().toString(), m.getName(), m.getGenericName(),
                m.getManufacturer(), m.getBarcode(), m.getForm(), m.getStrength(),
                m.getLeafletSections(), m.isVectorIndexed());
    }
    private static String emptyToNull(String s) { return s == null || s.isBlank() ? null : s; }
}
```

#### `backend/src/main/java/com/eczam/medications/MedicationController.java`

```java
package com.eczam.medications;

import com.eczam.medications.dto.MedicationDtos.*;
import com.eczam.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/medications")
public class MedicationController {

    private final MedicationService service;
    public MedicationController(MedicationService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<MedicationView>> list(@RequestParam(required = false) String q,
                                                  @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(service.search(q, limit), service.cursorMeta(limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<MedicationDetail> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MedicationDetail> create(@Valid @RequestBody CreateMedicationRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @GetMapping("/barcode/{code}")
    public ApiResponse<MedicationDetail> byBarcode(@PathVariable String code) {
        return ApiResponse.ok(service.lookupByBarcode(code));
    }

    @GetMapping("/{id}/leaflet")
    public ApiResponse<MedicationDetail> leaflet(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @GetMapping("/{id}/leaflet/search")
    public ApiResponse<LeafletSearchResult> searchLeaflet(@PathVariable UUID id, @RequestParam String q) {
        return ApiResponse.ok(service.searchLeaflet(id, q));
    }
}
```

### Inventory (`user_medications`)

#### `backend/src/main/java/com/eczam/inventory/UserMedication.java`

```java
package com.eczam.inventory;

import com.eczam.medications.Medication;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_medications",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "medication_id", "expiration_date"}))
@Getter @Setter @NoArgsConstructor
public class UserMedication {

    @Id @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "medication_id", nullable = false)
    private Medication medication;

    @Column(nullable = false)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(nullable = false)
    private String unit = "pill";

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @Column(columnDefinition = "text")
    private String notes;

    @CreationTimestamp @Column(name = "added_at", updatable = false)
    private OffsetDateTime addedAt;

    @UpdateTimestamp @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
```

#### `backend/src/main/java/com/eczam/inventory/UserMedicationRepository.java`

```java
package com.eczam.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserMedicationRepository extends JpaRepository<UserMedication, UUID> {
    List<UserMedication> findByUserIdOrderByAddedAtDesc(UUID userId);
    Optional<UserMedication> findByIdAndUserId(UUID id, UUID userId);
    boolean existsByUserIdAndMedicationIdAndExpirationDate(UUID userId, UUID medicationId, java.time.LocalDate expirationDate);
}
```

#### `backend/src/main/java/com/eczam/inventory/dto/InventoryDtos.java`

```java
package com.eczam.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class InventoryDtos {

    public enum ExpiryStatus { OK, EXPIRING_SOON, EXPIRED }

    public record InventoryItem(
            String id, String medicationId, String medicationName, String strength, String form,
            BigDecimal quantity, String unit, LocalDate expirationDate, String notes,
            boolean lowStock, ExpiryStatus expiryStatus) {}

    public record CreateInventoryRequest(
            @NotNull String medicationId,
            @NotNull @DecimalMin("0.0") BigDecimal quantity,
            @Size(max = 20) String unit,
            LocalDate expirationDate,
            String notes) {}

    public record UpdateInventoryRequest(
            @DecimalMin("0.0") BigDecimal quantity,
            @Size(max = 20) String unit,
            LocalDate expirationDate,
            String notes) {}

    private InventoryDtos() {}
}
```

#### `backend/src/main/java/com/eczam/inventory/UserMedicationService.java`

```java
package com.eczam.inventory;

import com.eczam.inventory.dto.InventoryDtos.*;
import com.eczam.medications.Medication;
import com.eczam.medications.MedicationRepository;
import com.eczam.shared.web.ApiException;
import com.eczam.shared.web.ErrorCode;
import com.eczam.users.User;
import com.eczam.users.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class UserMedicationService {

    private final UserMedicationRepository repo;
    private final MedicationRepository medications;
    private final UserRepository users;

    public UserMedicationService(UserMedicationRepository repo, MedicationRepository medications, UserRepository users) {
        this.repo = repo; this.medications = medications; this.users = users;
    }

    @Transactional(readOnly = true)
    public List<InventoryItem> list(UUID userId) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        int lowThreshold = u.getNotificationPreferences().lowStockThreshold();
        int expiryDays = u.getNotificationPreferences().expiryWarningDays();
        return repo.findByUserIdOrderByAddedAtDesc(userId).stream()
                .map(um -> toItem(um, lowThreshold, expiryDays)).toList();
    }

    @Transactional
    public InventoryItem create(UUID userId, CreateInventoryRequest req) {
        UUID medId = UUID.fromString(req.medicationId());
        Medication med = medications.findById(medId)
                .orElseThrow(() -> ApiException.notFound("Medication not found"));
        if (repo.existsByUserIdAndMedicationIdAndExpirationDate(userId, medId, req.expirationDate())) {
            throw ApiException.conflict(ErrorCode.INVENTORY_BATCH_EXISTS,
                    "This medication with the same expiry is already in your inventory");
        }
        UserMedication um = new UserMedication();
        um.setUserId(userId);
        um.setMedication(med);
        um.setQuantity(req.quantity());
        if (req.unit() != null) um.setUnit(req.unit());
        um.setExpirationDate(req.expirationDate());
        um.setNotes(req.notes());
        repo.save(um);
        return toItem(um, prefsLow(userId), prefsExpiry(userId));
    }

    @Transactional
    public InventoryItem update(UUID userId, UUID id, UpdateInventoryRequest req) {
        UserMedication um = load(userId, id);
        if (req.quantity() != null) um.setQuantity(req.quantity());
        if (req.unit() != null) um.setUnit(req.unit());
        if (req.expirationDate() != null) um.setExpirationDate(req.expirationDate());
        if (req.notes() != null) um.setNotes(req.notes());
        return toItem(um, prefsLow(userId), prefsExpiry(userId));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        repo.delete(load(userId, id));
    }

    @Transactional(readOnly = true)
    public InventoryItem get(UUID userId, UUID id) {
        return toItem(load(userId, id), prefsLow(userId), prefsExpiry(userId));
    }

    UserMedication load(UUID userId, UUID id) {
        return repo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Inventory entry not found"));
    }

    private int prefsLow(UUID userId) {
        return users.findById(userId).map(u -> u.getNotificationPreferences().lowStockThreshold()).orElse(7);
    }
    private int prefsExpiry(UUID userId) {
        return users.findById(userId).map(u -> u.getNotificationPreferences().expiryWarningDays()).orElse(30);
    }

    static InventoryItem toItem(UserMedication um, int lowThreshold, int expiryDays) {
        boolean lowStock = um.getQuantity().doubleValue() <= lowThreshold;
        ExpiryStatus status = expiryStatus(um.getExpirationDate(), expiryDays);
        Medication m = um.getMedication();
        return new InventoryItem(um.getId().toString(), m.getId().toString(), m.getName(),
                m.getStrength(), m.getForm(), um.getQuantity(), um.getUnit(),
                um.getExpirationDate(), um.getNotes(), lowStock, status);
    }

    static ExpiryStatus expiryStatus(LocalDate expiry, int warningDays) {
        if (expiry == null) return ExpiryStatus.OK;
        LocalDate today = LocalDate.now();
        if (expiry.isBefore(today)) return ExpiryStatus.EXPIRED;
        if (!expiry.isAfter(today.plusDays(warningDays))) return ExpiryStatus.EXPIRING_SOON;
        return ExpiryStatus.OK;
    }
}
```

#### `backend/src/main/java/com/eczam/inventory/UserMedicationController.java`

```java
package com.eczam.inventory;

import com.eczam.inventory.dto.InventoryDtos.*;
import com.eczam.shared.security.CurrentUser;
import com.eczam.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/user-medications")
public class UserMedicationController {

    private final UserMedicationService service;
    public UserMedicationController(UserMedicationService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<InventoryItem>> list(@CurrentUser UUID userId) {
        return ApiResponse.ok(service.list(userId));
    }

    @GetMapping("/{id}")
    public ApiResponse<InventoryItem> get(@CurrentUser UUID userId, @PathVariable UUID id) {
        return ApiResponse.ok(service.get(userId, id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InventoryItem> create(@CurrentUser UUID userId,
                                             @Valid @RequestBody CreateInventoryRequest req) {
        return ApiResponse.ok(service.create(userId, req));
    }

    @PatchMapping("/{id}")
    public ApiResponse<InventoryItem> update(@CurrentUser UUID userId, @PathVariable UUID id,
                                             @Valid @RequestBody UpdateInventoryRequest req) {
        return ApiResponse.ok(service.update(userId, id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser UUID userId, @PathVariable UUID id) {
        service.delete(userId, id);
    }
}
```

### Backend test (representative)

#### `backend/src/test/java/com/eczam/inventory/ExpiryStatusTest.java`

```java
package com.eczam.inventory;

import com.eczam.inventory.dto.InventoryDtos.ExpiryStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ExpiryStatusTest {
    @Test void expired_when_before_today() {
        assertThat(UserMedicationService.expiryStatus(LocalDate.now().minusDays(1), 30))
                .isEqualTo(ExpiryStatus.EXPIRED);
    }
    @Test void expiring_soon_within_window() {
        assertThat(UserMedicationService.expiryStatus(LocalDate.now().plusDays(10), 30))
                .isEqualTo(ExpiryStatus.EXPIRING_SOON);
    }
    @Test void ok_beyond_window() {
        assertThat(UserMedicationService.expiryStatus(LocalDate.now().plusDays(60), 30))
                .isEqualTo(ExpiryStatus.OK);
    }
}
```

---

## 3. Frontend

### `frontend/src/services/medicationService.ts`

```ts
import { apiClient } from "./apiClient";
import type { ApiResponse } from "../types";

export interface LeafletSections {
  dosage?: string; side_effects?: string; contraindications?: string;
  storage?: string; interactions?: string; missed_dose?: string;
}
export interface MedicationDetail {
  id: string; name: string; genericName?: string; manufacturer?: string;
  barcode?: string; form?: string; strength?: string;
  leafletSections?: LeafletSections; vectorIndexed: boolean;
}
export interface LeafletHit { section: string; snippet: string; }

export async function getByBarcode(code: string): Promise<MedicationDetail> {
  const r = await apiClient.get<ApiResponse<MedicationDetail>>(`/medications/barcode/${encodeURIComponent(code)}`);
  return r.data.data!;
}
export async function createMedication(payload: Partial<MedicationDetail> & { name: string }) {
  const r = await apiClient.post<ApiResponse<MedicationDetail>>("/medications", payload);
  return r.data.data!;
}
export async function getMedication(id: string): Promise<MedicationDetail> {
  const r = await apiClient.get<ApiResponse<MedicationDetail>>(`/medications/${id}`);
  return r.data.data!;
}
export async function searchLeaflet(id: string, q: string): Promise<LeafletHit[]> {
  const r = await apiClient.get<ApiResponse<{ hits: LeafletHit[] }>>(`/medications/${id}/leaflet/search`, { params: { q } });
  return r.data.data!.hits;
}
```

### `frontend/src/services/inventoryService.ts`

```ts
import { apiClient } from "./apiClient";
import type { ApiResponse } from "../types";

export type ExpiryStatus = "OK" | "EXPIRING_SOON" | "EXPIRED";
export interface InventoryItem {
  id: string; medicationId: string; medicationName: string; strength?: string; form?: string;
  quantity: number; unit: string; expirationDate?: string; notes?: string;
  lowStock: boolean; expiryStatus: ExpiryStatus;
}

export async function listInventory(): Promise<InventoryItem[]> {
  const r = await apiClient.get<ApiResponse<InventoryItem[]>>("/user-medications");
  return r.data.data!;
}
export async function addInventory(payload: {
  medicationId: string; quantity: number; unit?: string; expirationDate?: string; notes?: string;
}): Promise<InventoryItem> {
  const r = await apiClient.post<ApiResponse<InventoryItem>>("/user-medications", payload);
  return r.data.data!;
}
export async function updateInventory(id: string, payload: Partial<InventoryItem>) {
  const r = await apiClient.patch<ApiResponse<InventoryItem>>(`/user-medications/${id}`, payload);
  return r.data.data!;
}
export async function deleteInventory(id: string): Promise<void> {
  await apiClient.delete(`/user-medications/${id}`);
}
```

### `frontend/src/hooks/useBarcode.ts`

```ts
import { useEffect, useRef, useState } from "react";
import { Html5Qrcode } from "html5-qrcode";

/** Wraps html5-qrcode camera scanning. Renders into the element with id=`regionId`. */
export function useBarcode(regionId: string, onDecode: (code: string) => void, active: boolean) {
  const scannerRef = useRef<Html5Qrcode | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!active) return;
    const scanner = new Html5Qrcode(regionId);
    scannerRef.current = scanner;
    scanner
      .start({ facingMode: "environment" }, { fps: 10, qrbox: 250 },
        (decoded) => { onDecode(decoded); },
        () => {})
      .catch(() => setError("Kameraya erişilemedi. Lütfen elle giriş yapın."));
    return () => { scanner.stop().then(() => scanner.clear()).catch(() => {}); };
  }, [active, regionId, onDecode]);

  return { error };
}
```

### `frontend/src/features/medications/BarcodeScanner.tsx`

```tsx
import { useBarcode } from "../../hooks/useBarcode";

export default function BarcodeScanner({ onDecode, onCancel }: {
  onDecode: (code: string) => void; onCancel: () => void;
}) {
  const { error } = useBarcode("scanner-region", onDecode, true);
  return (
    <div role="dialog" aria-label="Barkod tarayıcı" className="rounded border p-4">
      <div id="scanner-region" className="mx-auto w-full max-w-sm" />
      {error && <p role="alert" className="mt-2 text-red-700">{error}</p>}
      <button onClick={onCancel} className="mt-3 rounded border px-4 py-2 text-lg">Elle giriş yap</button>
    </div>
  );
}
```

### `frontend/src/features/medications/AddMedicationForm.tsx`

```tsx
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import BarcodeScanner from "./BarcodeScanner";
import { createMedication, getByBarcode, type MedicationDetail } from "../../services/medicationService";
import { addInventory } from "../../services/inventoryService";

export default function AddMedicationForm() {
  const nav = useNavigate();
  const [mode, setMode] = useState<"choose" | "scan" | "form">("choose");
  const [med, setMed] = useState<Partial<MedicationDetail>>({});
  const [quantity, setQuantity] = useState(30);
  const [unit, setUnit] = useState("pill");
  const [expirationDate, setExpirationDate] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function onScanned(code: string) {
    try {
      const found = await getByBarcode(code);
      setMed(found); setMode("form");
    } catch {
      setMed({ barcode: code }); setMode("form");
      setError("Barkod bulunamadı. Lütfen bilgileri elle tamamlayın.");
    }
  }

  async function onSave() {
    setError(null);
    try {
      let medicationId = med.id;
      if (!medicationId) {
        const created = await createMedication({ name: med.name!, barcode: med.barcode,
          manufacturer: med.manufacturer, form: med.form, strength: med.strength });
        medicationId = created.id;
      }
      await addInventory({ medicationId: medicationId!, quantity, unit,
        expirationDate: expirationDate || undefined });
      nav("/inventory");
    } catch (e: any) {
      const code = e?.response?.data?.error?.code;
      setError(code === "INVENTORY_BATCH_EXISTS" ? "Bu ilaç ve son kullanma tarihi zaten envanterde." : "Kaydedilemedi.");
    }
  }

  if (mode === "choose") return (
    <div className="space-y-4 p-6">
      <h1 className="text-3xl font-bold">İlaç Ekle</h1>
      <button onClick={() => setMode("scan")} className="w-full rounded bg-blue-700 p-3 text-lg text-white">Barkod Tara</button>
      <button onClick={() => setMode("form")} className="w-full rounded border p-3 text-lg">Elle Ekle</button>
    </div>
  );

  if (mode === "scan") return (
    <div className="p-6"><BarcodeScanner onDecode={onScanned} onCancel={() => setMode("form")} /></div>
  );

  return (
    <main className="mx-auto max-w-md space-y-4 p-6">
      <h1 className="text-3xl font-bold">İlaç Bilgileri</h1>
      {error && <p role="alert" className="text-amber-700">{error}</p>}
      <label className="block"><span className="text-lg">İlaç adı</span>
        <input required value={med.name ?? ""} onChange={(e) => setMed({ ...med, name: e.target.value })}
               className="mt-1 w-full rounded border p-3 text-lg" /></label>
      <label className="block"><span className="text-lg">Üretici</span>
        <input value={med.manufacturer ?? ""} onChange={(e) => setMed({ ...med, manufacturer: e.target.value })}
               className="mt-1 w-full rounded border p-3 text-lg" /></label>
      <div className="flex gap-3">
        <label className="block flex-1"><span className="text-lg">Adet</span>
          <input type="number" min={0} value={quantity} onChange={(e) => setQuantity(Number(e.target.value))}
                 className="mt-1 w-full rounded border p-3 text-lg" /></label>
        <label className="block flex-1"><span className="text-lg">Birim</span>
          <input value={unit} onChange={(e) => setUnit(e.target.value)}
                 className="mt-1 w-full rounded border p-3 text-lg" /></label>
      </div>
      <label className="block"><span className="text-lg">Son kullanma tarihi</span>
        <input type="date" value={expirationDate} onChange={(e) => setExpirationDate(e.target.value)}
               className="mt-1 w-full rounded border p-3 text-lg" /></label>
      <button onClick={onSave} disabled={!med.name}
              className="w-full rounded bg-blue-700 p-3 text-lg font-semibold text-white disabled:opacity-50">
        Kaydet
      </button>
    </main>
  );
}
```

### `frontend/src/pages/Inventory.tsx`

```tsx
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { listInventory, type InventoryItem } from "../services/inventoryService";

function ExpiryBadge({ status }: { status: InventoryItem["expiryStatus"] }) {
  if (status === "EXPIRED") return <span className="rounded bg-red-100 px-2 py-1 text-red-800">Süresi doldu</span>;
  if (status === "EXPIRING_SOON") return <span className="rounded bg-amber-100 px-2 py-1 text-amber-800">Yakında dolacak</span>;
  return null;
}

export default function Inventory() {
  const { data, isLoading } = useQuery({ queryKey: ["inventory"], queryFn: listInventory });
  if (isLoading) return <p className="p-6 text-xl">Yükleniyor…</p>;

  return (
    <main className="mx-auto max-w-2xl p-6">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-3xl font-bold">Envanter</h1>
        <Link to="/inventory/add" className="rounded bg-blue-700 px-4 py-2 text-lg text-white">+ Ekle</Link>
      </div>
      <ul className="space-y-3">
        {data?.map((item) => (
          <li key={item.id} className="rounded border p-4">
            <Link to={`/medications/${item.medicationId}`} className="block">
              <div className="flex items-center justify-between">
                <span className="text-xl font-semibold">{item.medicationName}</span>
                <span className="text-lg">{item.quantity} {item.unit}</span>
              </div>
              <div className="mt-2 flex gap-2">
                {item.lowStock && <span className="rounded bg-orange-100 px-2 py-1 text-orange-800">Az kaldı</span>}
                <ExpiryBadge status={item.expiryStatus} />
              </div>
            </Link>
          </li>
        ))}
        {data?.length === 0 && <p className="text-lg text-gray-600">Henüz ilaç eklemediniz.</p>}
      </ul>
    </main>
  );
}
```

### `frontend/src/pages/MedicationDetail.tsx`

```tsx
import { useState } from "react";
import { useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { getMedication, searchLeaflet, type LeafletHit } from "../services/medicationService";

const SECTION_LABELS: Record<string, string> = {
  dosage: "Doz", side_effects: "Yan etkiler", contraindications: "Kullanılmaması gereken durumlar",
  storage: "Saklama", interactions: "Etkileşimler", missed_dose: "Doz atlanırsa",
};

export default function MedicationDetail() {
  const { id } = useParams<{ id: string }>();
  const { data: med, isLoading } = useQuery({ queryKey: ["medication", id], queryFn: () => getMedication(id!) });
  const [q, setQ] = useState("");
  const [hits, setHits] = useState<LeafletHit[] | null>(null);

  if (isLoading || !med) return <p className="p-6 text-xl">Yükleniyor…</p>;

  async function onSearch() { if (q.trim()) setHits(await searchLeaflet(id!, q)); }
  const sections = med.leafletSections ?? {};

  return (
    <main className="mx-auto max-w-2xl p-6">
      <h1 className="text-3xl font-bold">{med.name}</h1>
      {med.strength && <p className="text-lg text-gray-600">{med.strength} · {med.form}</p>}

      <div className="mt-6 flex gap-2">
        <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Prospektüste ara…"
               className="flex-1 rounded border p-3 text-lg" />
        <button onClick={onSearch} className="rounded bg-blue-700 px-4 text-lg text-white">Ara</button>
      </div>

      {hits && (
        <section className="mt-4">
          <h2 className="text-xl font-semibold">Arama sonuçları</h2>
          {hits.length === 0 ? <p className="text-gray-600">Sonuç yok.</p> :
            <ul className="space-y-2">{hits.map((h, i) =>
              <li key={i} className="rounded bg-yellow-50 p-3">
                <strong>{SECTION_LABELS[h.section] ?? h.section}:</strong> {h.snippet}
              </li>)}</ul>}
        </section>
      )}

      <section className="mt-6 space-y-4">
        {Object.entries(SECTION_LABELS).map(([key, label]) => {
          const text = (sections as Record<string, string | undefined>)[key];
          if (!text) return null;
          return (
            <details key={key} className="rounded border p-4" open>
              <summary className="cursor-pointer text-xl font-semibold">{label}</summary>
              <p className="mt-2 whitespace-pre-line text-lg">{text}</p>
            </details>
          );
        })}
      </section>
      {/* TTS control bar added in Phase 5 */}
    </main>
  );
}
```

### Wire new routes — update `frontend/src/App.tsx`

```tsx
// add imports
import Inventory from "./pages/Inventory";
import MedicationDetail from "./pages/MedicationDetail";
import AddMedicationForm from "./features/medications/AddMedicationForm";

// inside <Route element={<ProtectedRoute />}> add:
<Route path="/inventory" element={<Inventory />} />
<Route path="/inventory/add" element={<AddMedicationForm />} />
<Route path="/medications/:id" element={<MedicationDetail />} />
```

---

## 4. Exit criteria (Phase 2)

- [ ] Add a medication by barcode (OpenFDA fallback creates the catalog row + auto-fills).
- [ ] Add a medication manually when not found; graceful fallback message shown.
- [ ] Inventory list shows quantity, low-stock and expiry badges.
- [ ] Edit and delete inventory entries; same med + same expiry → 409 handled.
- [ ] Medication detail shows leaflet sections and full-text section search works.

## 5. Tests (Phase 2)

- Unit: `ExpiryStatusTest`; low-stock threshold logic.
- Integration: `/medications/barcode/{code}` (local hit, OpenFDA mock, 404 miss);
  `/user-medications` CRUD + batch-uniqueness 409; authorization (user B cannot read
  user A's entries).
- Frontend: AddMedicationForm (manual + scan paths), Inventory badges.

Covers FR-010…026, FR-080…083, UC-002/003. Next:
[phase-3-scheduling-logging.md](phase-3-scheduling-logging.md).
