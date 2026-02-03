package com.oolshik.backend.service;

import com.oolshik.backend.config.NotificationProperties;
import com.oolshik.backend.entity.HelpRequestEntity;
import com.oolshik.backend.repo.HelpRequestCandidateRepository;
import com.oolshik.backend.repo.HelpRequestNotificationAudienceRepository;
import com.oolshik.backend.repo.HelperLocationRepository;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HelpRequestCandidateServiceTest {

    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    @Test
    void seedCandidatesForNewRequestInsertsCandidatesAndAudience() {
        HelperLocationRepository helperRepo = mock(HelperLocationRepository.class);
        HelpRequestCandidateRepository candidateRepo = mock(HelpRequestCandidateRepository.class);
        HelpRequestNotificationAudienceRepository audienceRepo = mock(HelpRequestNotificationAudienceRepository.class);
        NotificationProperties properties = new NotificationProperties();

        HelpRequestCandidateService service = new HelpRequestCandidateService(helperRepo, candidateRepo, audienceRepo, properties);

        UUID requestId = UUID.randomUUID();
        UUID helperA = UUID.randomUUID();
        UUID helperB = UUID.randomUUID();

        HelpRequestEntity task = new HelpRequestEntity();
        task.setId(requestId);
        task.setRadiusMeters(1000);
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(72.0, 19.0));
        task.setLocation(point);

        when(helperRepo.findEligibleHelpersForRequest(eq(requestId), anyDouble(), any()))
                .thenReturn(List.of(helperA, helperB));

        service.seedCandidatesForNewRequest(task, OffsetDateTime.now());

        verify(candidateRepo).insertIgnore(any(), eq(requestId), eq(helperA), anyString());
        verify(candidateRepo).insertIgnore(any(), eq(requestId), eq(helperB), anyString());

        ArgumentCaptor<Integer> radiusCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(audienceRepo).insertIgnore(any(), eq(requestId), eq(helperA), anyString(), radiusCaptor.capture());
        assertEquals(1000, radiusCaptor.getValue());
    }

    @Test
    void seedCandidatesForRadiusExpansionUsesNewlyEligible() {
        HelperLocationRepository helperRepo = mock(HelperLocationRepository.class);
        HelpRequestCandidateRepository candidateRepo = mock(HelpRequestCandidateRepository.class);
        HelpRequestNotificationAudienceRepository audienceRepo = mock(HelpRequestNotificationAudienceRepository.class);
        NotificationProperties properties = new NotificationProperties();

        HelpRequestCandidateService service = new HelpRequestCandidateService(helperRepo, candidateRepo, audienceRepo, properties);

        UUID requestId = UUID.randomUUID();
        UUID helperA = UUID.randomUUID();
        HelpRequestEntity task = new HelpRequestEntity();
        task.setId(requestId);
        task.setRadiusMeters(2000);
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(72.0, 19.0));
        task.setLocation(point);

        when(helperRepo.findNewlyEligibleHelpersForRequest(eq(requestId), anyDouble(), anyDouble(), any()))
                .thenReturn(List.of(helperA));

        service.seedCandidatesForRadiusExpansion(task, 1000, 2000, OffsetDateTime.now());

        verify(candidateRepo).insertIgnore(any(), eq(requestId), eq(helperA), anyString());
        verify(audienceRepo).insertIgnore(any(), eq(requestId), eq(helperA), anyString(), eq(2000));
    }
}
