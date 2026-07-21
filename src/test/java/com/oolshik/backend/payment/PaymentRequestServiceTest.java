package com.oolshik.backend.payment;

import com.oolshik.backend.domain.PaymentProfileSourceType;
import com.oolshik.backend.entity.HelpRequestEntity;
import com.oolshik.backend.entity.PaymentProfileEntity;
import com.oolshik.backend.payment.dto.PaymentDtos.CreatePaymentRequest;
import com.oolshik.backend.repo.HelpRequestRepository;
import com.oolshik.backend.service.PaymentNotificationService;
import com.oolshik.backend.service.PaymentProfileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRequestServiceTest {

    @Mock
    private PaymentRequestRepository repo;

    @Mock
    private HelpRequestRepository helpRequestRepository;

    @Mock
    private PaymentNotificationService paymentNotificationService;

    @Mock
    private PaymentProfileService paymentProfileService;

    @Test
    void requesterPaysScanDiffersFromProfile_storesBothVpas() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID helperId = UUID.randomUUID();

        HelpRequestEntity task = new HelpRequestEntity();
        task.setId(taskId);
        task.setRequesterId(requesterId);
        task.setHelperId(helperId);

        PaymentProfileEntity helperProfile = new PaymentProfileEntity();
        helperProfile.setUpiId("helper@ybl");
        helperProfile.setPayeeLabel("Helper Name");
        helperProfile.setSourceType(PaymentProfileSourceType.MANUAL);

        when(repo.findFirstByTaskIdAndPaymentModeAndPayerRoleAndStatusInOrderByCreatedAtDesc(
                any(), any(), any(), any())).thenReturn(Optional.empty());
        when(helpRequestRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(paymentProfileService.requireActiveProfile(helperId)).thenReturn(helperProfile);
        when(paymentProfileService.resolvePayeeLabel(helperId, helperProfile)).thenReturn("Helper Name");
        when(repo.save(any(PaymentRequest.class))).thenAnswer(inv -> inv.getArgument(0, PaymentRequest.class));

        CreatePaymentRequest body = new CreatePaymentRequest(
                taskId,
                "upi://pay?pa=scanned@okhdfc&pn=Scanned%20Shop&am=250.00&cu=INR",
                "upi-uri",
                "scanned@okhdfc",
                "Scanned Shop",
                null,
                null,
                null,
                new BigDecimal("250.00"),
                "INR",
                null,
                null,
                "1.0",
                "device-1",
                PaymentPayerRole.REQUESTER
        );

        PaymentRequestService service = new PaymentRequestService(
                repo, helpRequestRepository, paymentNotificationService, paymentProfileService);
        PaymentRequest saved = service.create(helperId, "127.0.0.1", body);

        // The resolved payee stays the helper's saved profile (backward-compatible primary destination).
        assertEquals("helper@ybl", saved.getPayeeVpa());
        assertEquals("Helper Name", saved.getPayeeName());
        // The scanned QR's identity is preserved separately instead of being discarded.
        assertEquals("scanned@okhdfc", saved.getScannedPayeeVpa());
        assertEquals("Scanned Shop", saved.getScannedPayeeName());
    }

    @Test
    void requesterPaysNoScanVpa_scannedFieldsStayNull() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID helperId = UUID.randomUUID();

        HelpRequestEntity task = new HelpRequestEntity();
        task.setId(taskId);
        task.setRequesterId(requesterId);
        task.setHelperId(helperId);

        PaymentProfileEntity helperProfile = new PaymentProfileEntity();
        helperProfile.setUpiId("helper@ybl");
        helperProfile.setPayeeLabel("Helper Name");
        helperProfile.setSourceType(PaymentProfileSourceType.MANUAL);

        when(repo.findFirstByTaskIdAndPaymentModeAndPayerRoleAndStatusInOrderByCreatedAtDesc(
                any(), any(), any(), any())).thenReturn(Optional.empty());
        when(helpRequestRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(paymentProfileService.requireActiveProfile(helperId)).thenReturn(helperProfile);
        when(paymentProfileService.resolvePayeeLabel(helperId, helperProfile)).thenReturn("Helper Name");
        when(repo.save(any(PaymentRequest.class))).thenAnswer(inv -> inv.getArgument(0, PaymentRequest.class));

        CreatePaymentRequest body = new CreatePaymentRequest(
                taskId,
                "some-raw-payload",
                "unknown",
                null,
                null,
                null,
                null,
                null,
                new BigDecimal("250.00"),
                "INR",
                null,
                null,
                "1.0",
                "device-1",
                PaymentPayerRole.REQUESTER
        );

        PaymentRequestService service = new PaymentRequestService(
                repo, helpRequestRepository, paymentNotificationService, paymentProfileService);
        PaymentRequest saved = service.create(helperId, "127.0.0.1", body);

        assertNull(saved.getScannedPayeeVpa());
        assertNull(saved.getScannedPayeeName());
    }

    @Test
    void helperPaysViaScan_scannedFieldsStayNull() throws Exception {
        UUID taskId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID helperId = UUID.randomUUID();

        HelpRequestEntity task = new HelpRequestEntity();
        task.setId(taskId);
        task.setRequesterId(requesterId);
        task.setHelperId(helperId);

        when(repo.findFirstByTaskIdAndPaymentModeAndPayerRoleAndStatusInOrderByCreatedAtDesc(
                any(), any(), any(), any())).thenReturn(Optional.empty());
        when(helpRequestRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(repo.save(any(PaymentRequest.class))).thenAnswer(inv -> inv.getArgument(0, PaymentRequest.class));

        CreatePaymentRequest body = new CreatePaymentRequest(
                taskId,
                "upi://pay?pa=requester@okhdfc&pn=Requester%20Name&am=250.00&cu=INR",
                "upi-uri",
                "requester@okhdfc",
                "Requester Name",
                null,
                null,
                null,
                new BigDecimal("250.00"),
                "INR",
                null,
                null,
                "1.0",
                "device-1",
                PaymentPayerRole.HELPER
        );

        PaymentRequestService service = new PaymentRequestService(
                repo, helpRequestRepository, paymentNotificationService, paymentProfileService);
        PaymentRequest saved = service.create(helperId, "127.0.0.1", body);

        // The helper-pays path uses the scanned VPA directly as the primary payee (unchanged behavior)
        // and has no separate profile-vs-scan distinction, so the new field is not populated.
        assertEquals("requester@okhdfc", saved.getPayeeVpa());
        assertNull(saved.getScannedPayeeVpa());
    }

    @Test
    void rescanWithMatchingAmountRefreshesStaleScannedVpaOnExistingPendingRequest() throws Exception {
        UUID taskId = UUID.randomUUID();

        PaymentRequest existing = PaymentRequest.builder()
                .id(UUID.randomUUID())
                .taskId(taskId)
                .payerRole(PaymentPayerRole.REQUESTER)
                .paymentMode(PaymentMode.MERCHANT_QR)
                .status(PaymentRequestService.STATUS_PENDING)
                .amountRequested(new BigDecimal("250.00"))
                .payeeVpa("helper@ybl")
                .payeeName("Helper Name")
                .scannedPayeeVpa("wrong-shop@okhdfc")
                .scannedPayeeName("Wrong Shop")
                .build();

        when(repo.findFirstByTaskIdAndPaymentModeAndPayerRoleAndStatusInOrderByCreatedAtDesc(
                any(), any(), any(), any())).thenReturn(Optional.of(existing));
        when(repo.save(any(PaymentRequest.class))).thenAnswer(inv -> inv.getArgument(0, PaymentRequest.class));

        // Helper mis-scanned once (wrong-shop@okhdfc), then rescans the correct QR at the same
        // amount. Without a refresh, the amount-match dedup would silently keep the stale scan.
        CreatePaymentRequest body = new CreatePaymentRequest(
                taskId,
                "upi://pay?pa=correct-shop@okhdfc&pn=Correct%20Shop&am=250.00&cu=INR",
                "upi-uri",
                "correct-shop@okhdfc",
                "Correct Shop",
                null,
                null,
                null,
                new BigDecimal("250.00"),
                "INR",
                null,
                null,
                "1.0",
                "device-1",
                PaymentPayerRole.REQUESTER
        );

        PaymentRequestService service = new PaymentRequestService(
                repo, helpRequestRepository, paymentNotificationService, paymentProfileService);
        PaymentRequest result = service.create(UUID.randomUUID(), "127.0.0.1", body);

        assertEquals(existing.getId(), result.getId());
        assertEquals("correct-shop@okhdfc", result.getScannedPayeeVpa());
        assertEquals("Correct Shop", result.getScannedPayeeName());
    }

    @Test
    void normalizedVpaEqualsIgnoresCaseAndSurroundingWhitespace() {
        assertTrue(PaymentRequestService.normalizedVpaEquals(" Helper@YBL ", "helper@ybl"));
        assertFalse(PaymentRequestService.normalizedVpaEquals("helper@ybl", "other@ybl"));
        assertFalse(PaymentRequestService.normalizedVpaEquals(null, "helper@ybl"));
    }
}
