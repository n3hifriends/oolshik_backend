package com.oolshik.backend.web;

import com.oolshik.backend.security.FirebaseTokenFilter;
import com.oolshik.backend.service.HelperLocationService;
import com.oolshik.backend.repo.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/helpers")
public class HelperLocationController {

    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    private final HelperLocationService service;
    private final UserRepository userRepo;

    public HelperLocationController(HelperLocationService service, UserRepository userRepo) {
        this.service = service;
        this.userRepo = userRepo;
    }

    @PostMapping("/location")
    public ResponseEntity<?> upsertLocation(
            @AuthenticationPrincipal FirebaseTokenFilter.FirebaseUserPrincipal principal,
            @RequestBody @Valid UpdateLocationRequest body
    ) {
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(body.longitude(), body.latitude()));
        var user = userRepo.findByPhoneNumber(principal.phone()).orElseThrow();
        service.upsert(user.getId(), point);
        return ResponseEntity.ok().build();
    }

    public record UpdateLocationRequest(
            @NotNull Double latitude,
            @NotNull Double longitude
    ) {}
}
