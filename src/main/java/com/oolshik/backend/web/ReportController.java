// src/main/java/com/oolshik/backend/web/ReportController.java
package com.oolshik.backend.web;

import com.oolshik.backend.service.ReportService;
import com.oolshik.backend.web.dto.ReportDtos.CreateRequest;
import com.oolshik.backend.web.dto.ReportDtos.CreateResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

//    @PostMapping
//    public ResponseEntity<CreateResponse> create(User principal, @RequestBody @Valid CreateRequest body) {
//        // principal.getUsername() is the phone number in your app
//        var res = reportService.create(principal.getUsername(), body);
//        return ResponseEntity.ok(res);
//    }

    @PostMapping
    public ResponseEntity<CreateResponse> create(Principal principal, @RequestBody CreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reportService.create(principal.getName(), req));
    }
}
