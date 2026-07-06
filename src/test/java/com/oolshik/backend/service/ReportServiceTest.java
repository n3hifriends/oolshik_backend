package com.oolshik.backend.service;

import com.oolshik.backend.domain.ReportPriority;
import com.oolshik.backend.domain.ReportReason;
import com.oolshik.backend.entity.HelpRequestEntity;
import com.oolshik.backend.entity.ReportEventEntity;
import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.repo.HelpRequestRepository;
import com.oolshik.backend.repo.ReportEventRepository;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.security.AuthenticatedUserPrincipal;
import com.oolshik.backend.web.dto.ReportDtos.CreateRequest;
import com.oolshik.backend.web.dto.ReportDtos.CreateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReportServiceTest {

    private UserRepository userRepo;
    private HelpRequestRepository helpRepo;
    private ReportEventRepository reportRepo;
    private CurrentUserService currentUserService;
    private ReportService service;

    private final UUID reporterId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();
    private final UUID taskId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userRepo = mock(UserRepository.class);
        helpRepo = mock(HelpRequestRepository.class);
        reportRepo = mock(ReportEventRepository.class);
        currentUserService = mock(CurrentUserService.class);
        service = new ReportService(userRepo, helpRepo, reportRepo, currentUserService);

        UserEntity reporter = new UserEntity();
        reporter.setId(reporterId);

        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
                "phone", null, "+910000000000", null, reporterId);
        when(currentUserService.resolve(any())).thenReturn(reporter);

        UserEntity target = new UserEntity();
        target.setId(targetId);
        when(userRepo.findById(targetId)).thenReturn(Optional.of(target));
        when(reportRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createsNormalReport_withExistingReason() {
        CreateRequest req = new CreateRequest(null, targetId, ReportReason.SPAM, null);
        AuthenticatedUserPrincipal principal = principal();

        CreateResponse resp = service.create(principal, req);

        assertThat(resp).isNotNull();
        ArgumentCaptor<ReportEventEntity> captor = ArgumentCaptor.forClass(ReportEventEntity.class);
        verify(reportRepo).save(captor.capture());
        ReportEventEntity saved = captor.getValue();
        assertThat(saved.getReason()).isEqualTo(ReportReason.SPAM);
        assertThat(saved.getPriority()).isEqualTo(ReportPriority.MEDIUM);
    }

    @Test
    void createsChildSafetyUserReport_withCriticalPriority() {
        CreateRequest req = new CreateRequest(null, targetId, ReportReason.CHILD_SAFETY,
                "User shared inappropriate content involving minors.");
        AuthenticatedUserPrincipal principal = principal();

        service.create(principal, req);

        ArgumentCaptor<ReportEventEntity> captor = ArgumentCaptor.forClass(ReportEventEntity.class);
        verify(reportRepo).save(captor.capture());
        ReportEventEntity saved = captor.getValue();
        assertThat(saved.getReason()).isEqualTo(ReportReason.CHILD_SAFETY);
        assertThat(saved.getPriority()).isEqualTo(ReportPriority.CRITICAL);
        assertThat(saved.getTargetUserId()).isEqualTo(targetId);
        assertThat(saved.getDetails()).isEqualTo("User shared inappropriate content involving minors.");
    }

    @Test
    void createsChildSafetyTaskReport_resolvesCounterpartyAsTarget() {
        UUID helperId = UUID.randomUUID();
        HelpRequestEntity hr = new HelpRequestEntity();
        hr.setId(taskId);
        hr.setRequesterId(targetId);
        hr.setHelperId(helperId);
        when(helpRepo.findById(taskId)).thenReturn(Optional.of(hr));

        // reporter is the helper
        UserEntity helperUser = new UserEntity();
        helperUser.setId(helperId);
        when(currentUserService.resolve(any())).thenReturn(helperUser);

        CreateRequest req = new CreateRequest(taskId, null, ReportReason.CHILD_SAFETY,
                "Saw suspicious behaviour on this task.");

        service.create(principal(), req);

        ArgumentCaptor<ReportEventEntity> captor = ArgumentCaptor.forClass(ReportEventEntity.class);
        verify(reportRepo).save(captor.capture());
        ReportEventEntity saved = captor.getValue();
        assertThat(saved.getReason()).isEqualTo(ReportReason.CHILD_SAFETY);
        assertThat(saved.getPriority()).isEqualTo(ReportPriority.CRITICAL);
        assertThat(saved.getTargetUserId()).isEqualTo(targetId); // requester is the target
        assertThat(saved.getHelpRequestId()).isEqualTo(taskId);
    }

    @Test
    void rejectsChildSafetyReport_withBlankDetails() {
        CreateRequest req = new CreateRequest(null, targetId, ReportReason.CHILD_SAFETY, "");

        assertThatThrownBy(() -> service.create(principal(), req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("errors.report.detailsRequiredForChildSafety");
    }

    @Test
    void rejectsBothTaskIdAndTargetUserId() {
        CreateRequest req = new CreateRequest(taskId, targetId, ReportReason.SPAM, null);

        assertThatThrownBy(() -> service.create(principal(), req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("errors.report.onlyOneTarget");
    }

    @Test
    void rejectsNeitherTaskIdNorTargetUserId() {
        CreateRequest req = new CreateRequest(null, null, ReportReason.SPAM, null);

        assertThatThrownBy(() -> service.create(principal(), req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("errors.report.taskOrTargetRequired");
    }

    @Test
    void rejectsSelfReport() {
        // target is the same as reporter
        UserEntity self = new UserEntity();
        self.setId(reporterId);
        when(userRepo.findById(reporterId)).thenReturn(Optional.of(self));

        CreateRequest req = new CreateRequest(null, reporterId, ReportReason.SPAM, null);

        assertThatThrownBy(() -> service.create(principal(), req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("errors.report.selfReportForbidden");
    }

    private AuthenticatedUserPrincipal principal() {
        return new AuthenticatedUserPrincipal("phone", null, "+910000000000", null, reporterId);
    }
}
