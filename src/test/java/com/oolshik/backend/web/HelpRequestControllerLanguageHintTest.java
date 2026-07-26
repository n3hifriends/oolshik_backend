package com.oolshik.backend.web;

import com.oolshik.backend.entity.HelpRequestEntity;
import com.oolshik.backend.entity.UserEntity;
import com.oolshik.backend.media.AudioFileRepository;
import com.oolshik.backend.media.AudioPlaybackUrlResolver;
import com.oolshik.backend.media.AudioStorageMetadataResolver;
import com.oolshik.backend.repo.UserRepository;
import com.oolshik.backend.service.CurrentUserService;
import com.oolshik.backend.service.HelpRequestRatingService;
import com.oolshik.backend.service.HelpRequestService;
import com.oolshik.backend.transcription.TranscriptionAudioSourceResolver;
import com.oolshik.backend.transcription.TranscriptionJobEntity;
import com.oolshik.backend.transcription.TranscriptionJobPublisher;
import com.oolshik.backend.transcription.TranscriptionJobService;
import com.oolshik.backend.web.dto.HelpRequestDtos.CreateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * A user's UI display-language preference must never be used as the STT language hint for a
 * recording -- it isn't a signal for what language the recording is actually in. This test
 * guards that regression: regardless of preferredLanguage, the job must always be created with
 * an explicit "auto" hint so the worker performs real language detection.
 */
@ExtendWith(MockitoExtension.class)
class HelpRequestControllerLanguageHintTest {

    @Mock private HelpRequestService service;
    @Mock private HelpRequestRatingService ratingService;
    @Mock private UserRepository userRepo;
    @Mock private CurrentUserService currentUserService;
    @Mock private AudioFileRepository audioRepo;
    @Mock private AudioPlaybackUrlResolver audioPlaybackUrlResolver;
    @Mock private AudioStorageMetadataResolver audioStorageMetadataResolver;
    @Mock private TranscriptionJobService transcriptionJobService;
    @Mock private TranscriptionJobPublisher transcriptionJobPublisher;
    @Mock private TranscriptionAudioSourceResolver transcriptionAudioSourceResolver;

    private HelpRequestController controller;

    @BeforeEach
    void setUp() {
        controller = new HelpRequestController(
                service, ratingService, userRepo, currentUserService, audioRepo,
                audioPlaybackUrlResolver, audioStorageMetadataResolver,
                transcriptionJobService, transcriptionJobPublisher, transcriptionAudioSourceResolver
        );
    }

    private void stubCommonCollaborators(HelpRequestEntity created) {
        when(ratingService.summaryForRequest(any(), any(), any(), any()))
                .thenReturn(new HelpRequestRatingService.RatingSummary(null, null, null, null));
        when(transcriptionAudioSourceResolver.resolveForJob(anyString())).thenReturn("https://audio.example.com/x.m4a");
        when(service.create(any(), any(), any(), any(), anyInt(), any(), any(), any(), any()))
                .thenReturn(created);

        TranscriptionJobEntity job = new TranscriptionJobEntity();
        job.setJobId(UUID.randomUUID());
        job.setTaskId(created.getId());
        when(transcriptionJobService.createOrGet(any(), any(), any(), any(), any(), any())).thenReturn(job);
    }

    private CreateRequest voiceOnlyRequest() {
        return new CreateRequest(
                null, null, "https://example.com/voice.m4a", null,
                12.34, 56.78, 500, null, null
        );
    }

    private HelpRequestEntity createdEntityWithVoice(UUID id) {
        HelpRequestEntity created = new HelpRequestEntity();
        created.setId(id);
        created.setVoiceUrl("https://example.com/voice.m4a");
        return created;
    }

    @Test
    void languageHintIsAlwaysAutoRegardlessOfPreferredLanguageEnIn() {
        UUID requesterId = UUID.randomUUID();
        UserEntity requester = new UserEntity();
        requester.setId(requesterId);
        requester.setPreferredLanguage("en-IN");
        when(currentUserService.require(any())).thenReturn(requester);

        HelpRequestEntity created = createdEntityWithVoice(UUID.randomUUID());
        stubCommonCollaborators(created);

        controller.create(null, voiceOnlyRequest());

        ArgumentCaptor<String> hintCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(transcriptionJobService).createOrGet(
                any(), any(), any(), hintCaptor.capture(), any(), any());
        assertEquals("auto", hintCaptor.getValue());
    }

    @Test
    void languageHintIsAlwaysAutoRegardlessOfPreferredLanguageMr() {
        UUID requesterId = UUID.randomUUID();
        UserEntity requester = new UserEntity();
        requester.setId(requesterId);
        requester.setPreferredLanguage("mr-IN");
        when(currentUserService.require(any())).thenReturn(requester);

        HelpRequestEntity created = createdEntityWithVoice(UUID.randomUUID());
        stubCommonCollaborators(created);

        controller.create(null, voiceOnlyRequest());

        ArgumentCaptor<String> hintCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(transcriptionJobService).createOrGet(
                any(), any(), any(), hintCaptor.capture(), any(), any());
        assertEquals("auto", hintCaptor.getValue());
    }

    @Test
    void languageHintIsAlwaysAutoWhenPreferredLanguageIsNull() {
        UUID requesterId = UUID.randomUUID();
        UserEntity requester = new UserEntity();
        requester.setId(requesterId);
        requester.setPreferredLanguage(null);
        when(currentUserService.require(any())).thenReturn(requester);

        HelpRequestEntity created = createdEntityWithVoice(UUID.randomUUID());
        stubCommonCollaborators(created);

        controller.create(null, voiceOnlyRequest());

        ArgumentCaptor<String> hintCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(transcriptionJobService).createOrGet(
                any(), any(), any(), hintCaptor.capture(), any(), any());
        assertEquals("auto", hintCaptor.getValue());
    }
}
