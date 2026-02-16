package com.oolshik.backend.media;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@RestController
@RequestMapping("/api/public/media/audio")
public class PublicAudioStreamController {

    private final StorageService storage;
    private final AudioFileRepository repo;
    private final boolean publicStreamEnabled;

    public PublicAudioStreamController(
            StorageService storage,
            AudioFileRepository repo,
            @Value("${media.local.publicStreamEnabled:false}") boolean publicStreamEnabled
    ) {
        this.storage = storage;
        this.repo = repo;
        this.publicStreamEnabled = publicStreamEnabled;
    }

    @GetMapping("/{id}/stream")
    public void stream(@PathVariable UUID id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!publicStreamEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Public audio stream is disabled");
        }

        AudioFile af = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Audio not found"));

        long fileLength = storage.size(af.getStorageKey());
        String range = request.getHeader("Range");
        long start = 0;
        long end = fileLength - 1;

        if (range != null && range.startsWith("bytes=")) {
            String[] parts = range.substring(6).split("-");
            start = Long.parseLong(parts[0]);
            if (parts.length > 1 && !parts[1].isEmpty()) {
                end = Long.parseLong(parts[1]);
            }
            if (end >= fileLength) {
                end = fileLength - 1;
            }
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        } else {
            response.setStatus(HttpServletResponse.SC_OK);
        }

        long contentLength = end - start + 1;
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("Content-Type", af.getMimeType());
        response.setHeader("Content-Length", Long.toString(contentLength));
        if (range != null) {
            response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
        }

        try (InputStream in = storage.readRange(af.getStorageKey(), start, end)) {
            in.transferTo(response.getOutputStream());
        }
    }
}
