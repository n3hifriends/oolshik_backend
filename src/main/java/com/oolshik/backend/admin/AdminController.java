package com.oolshik.backend.admin;

import com.oolshik.backend.admin.AdminDtos.AdminNotificationRow;
import com.oolshik.backend.admin.AdminDtos.AdminOtpAuditRow;
import com.oolshik.backend.admin.AdminDtos.AdminPaymentDetail;
import com.oolshik.backend.admin.AdminDtos.AdminPaymentRow;
import com.oolshik.backend.admin.AdminDtos.AddReportActionRequest;
import com.oolshik.backend.admin.AdminDtos.AdminReportDetail;
import com.oolshik.backend.admin.AdminDtos.AdminReportRow;
import com.oolshik.backend.admin.AdminDtos.AdminRequestDetail;
import com.oolshik.backend.admin.AdminDtos.AdminRequestSummary;
import com.oolshik.backend.admin.AdminDtos.AdminTranscriptionRow;
import com.oolshik.backend.admin.AdminDtos.AdminUserDetail;
import com.oolshik.backend.admin.AdminDtos.AdminUserSummary;
import com.oolshik.backend.admin.AdminDtos.AssignReportRequest;
import com.oolshik.backend.admin.AdminDtos.PageResponse;
import com.oolshik.backend.admin.AdminDtos.RetryTranscriptionResponse;
import com.oolshik.backend.admin.AdminDtos.StatsResponse;
import com.oolshik.backend.admin.AdminDtos.UpdateReportStatusRequest;
import com.oolshik.backend.admin.AdminDtos.UpdateRolesRequest;
import com.oolshik.backend.domain.HelpRequestStatus;
import com.oolshik.backend.domain.ReportReason;
import com.oolshik.backend.domain.ReportStatus;
import com.oolshik.backend.payment.PaymentMode;
import com.oolshik.backend.security.AuthenticatedUserPrincipal;
import com.oolshik.backend.transcription.TranscriptionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private static final int MAX_PAGE_SIZE = 100;

    private final AdminService adminService;

    @GetMapping("/stats")
    public StatsResponse getStats() {
        return adminService.getStats();
    }

    @GetMapping("/users")
    public PageResponse<AdminUserSummary> listUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminService.getUsers(
                role,
                search,
                pageRequest(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<AdminUserDetail> getUser(@PathVariable UUID id) {
        return adminService.getUser(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/users/{id}/roles")
    public AdminUserDetail updateRoles(
            @PathVariable UUID id,
            @RequestBody UpdateRolesRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return adminService.updateUserRoles(id, request.roles(), principal.userId());
    }

    @GetMapping("/users/{id}/requests")
    public List<AdminRequestSummary> getUserRequests(@PathVariable UUID id) {
        return adminService.getUserRequests(id);
    }

    @GetMapping("/users/{id}/otp")
    public List<AdminOtpAuditRow> getUserOtp(@PathVariable UUID id) {
        return adminService.getUserOtp(id);
    }

    @GetMapping("/requests")
    public PageResponse<AdminRequestSummary> listRequests(
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<HelpRequestStatus> parsed = statuses == null
                ? null
                : statuses.stream().map(this::parseHelpRequestStatus).toList();
        return adminService.getRequests(
                parsed,
                pageRequest(page, size, Sort.by(Sort.Direction.DESC, "lastStateChangeAt"))
        );
    }

    @GetMapping("/requests/{id}")
    public ResponseEntity<AdminRequestDetail> getRequest(@PathVariable UUID id) {
        return adminService.getRequest(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/otp-audit")
    public PageResponse<AdminOtpAuditRow> listOtpAudit(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminService.getOtpAudit(status, pageRequest(page, size));
    }

    @GetMapping("/transcription-jobs")
    public PageResponse<AdminTranscriptionRow> listTranscriptions(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminService.getTranscriptions(parseTranscriptionStatus(status), pageRequest(page, size));
    }

    @PostMapping("/transcription-jobs/retry-failed")
    public RetryTranscriptionResponse retryFailedTranscriptions(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return adminService.retryFailedTranscriptions();
    }

    @GetMapping("/payments")
    public PageResponse<AdminPaymentRow> listPayments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String mode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminService.getPayments(status, parsePaymentMode(mode), pageRequest(page, size));
    }

    @GetMapping("/payments/{id}")
    public ResponseEntity<AdminPaymentDetail> getPayment(@PathVariable UUID id) {
        return adminService.getPaymentDetail(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/reports")
    public PageResponse<AdminReportRow> listReports(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminService.getReports(
                parseReportStatus(status),
                parseReportReason(reason),
                targetType,
                search,
                pageRequest(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
    }

    @GetMapping("/reports/{id}")
    public ResponseEntity<AdminReportDetail> getReport(@PathVariable UUID id) {
        return adminService.getReport(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/reports/{id}/status")
    public AdminReportDetail updateReportStatus(
            @PathVariable UUID id,
            @RequestBody UpdateReportStatusRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        UUID adminId = requireAdmin(principal);
        return adminService.updateReportStatus(id, parseRequiredReportStatus(request.status()), request.note(), adminId);
    }

    @PatchMapping("/reports/{id}/assign")
    public AdminReportDetail assignReport(
            @PathVariable UUID id,
            @RequestBody AssignReportRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        UUID adminId = requireAdmin(principal);
        UUID assigneeId = request.adminUserId() == null ? adminId : request.adminUserId();
        return adminService.assignReport(id, assigneeId, adminId);
    }

    @PostMapping("/reports/{id}/actions")
    public AdminReportDetail addReportAction(
            @PathVariable UUID id,
            @RequestBody AddReportActionRequest request,
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        UUID adminId = requireAdmin(principal);
        return adminService.addReportAction(id, request.action(), request.note(), adminId);
    }

    @GetMapping("/notifications")
    public PageResponse<AdminNotificationRow> listNotifications(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminService.getNotifications(status, pageRequest(page, size));
    }

    private PageRequest pageRequest(int page, int size) {
        return pageRequest(page, size, Sort.unsorted());
    }

    private PageRequest pageRequest(int page, int size, Sort sort) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be greater than or equal to 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return PageRequest.of(page, size, sort);
    }

    private HelpRequestStatus parseHelpRequestStatus(String status) {
        return parseEnum(status, HelpRequestStatus.class, "status");
    }

    private PaymentMode parsePaymentMode(String mode) {
        return parseEnum(mode, PaymentMode.class, "mode");
    }

    private TranscriptionStatus parseTranscriptionStatus(String status) {
        return parseEnum(status, TranscriptionStatus.class, "transcription status");
    }

    private ReportStatus parseReportStatus(String status) {
        return parseEnum(status, ReportStatus.class, "report status");
    }

    private ReportStatus parseRequiredReportStatus(String status) {
        ReportStatus parsed = parseReportStatus(status);
        if (parsed == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Report status is required");
        }
        return parsed;
    }

    private ReportReason parseReportReason(String reason) {
        return parseEnum(reason, ReportReason.class, "report reason");
    }

    private UUID requireAdmin(AuthenticatedUserPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal.userId();
    }

    private <T extends Enum<T>> T parseEnum(String value, Class<T> enumType, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + label + ": " + value);
        }
    }
}
