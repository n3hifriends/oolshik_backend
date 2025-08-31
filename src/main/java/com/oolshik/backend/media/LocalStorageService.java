package com.oolshik.backend.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LocalStorageService implements StorageService {

    private final Path root;

    public LocalStorageService(@Value("${media.local.root:./data/audio}") String rootDir) throws IOException {
        this.root = Paths.get(rootDir).toAbsolutePath();
        Files.createDirectories(this.root.resolve("tmp"));
        Files.createDirectories(this.root.resolve("store"));
    }

    @Override
    public String writeTemp(String uploadId) throws IOException {
        Path p = root.resolve("tmp").resolve(uploadId + ".part");
        Files.deleteIfExists(p);
        Files.createFile(p);
        return "tmp/" + uploadId + ".part";
    }

    @Override
    public void append(String tempKey, byte[] bytes) throws IOException {
        Path p = root.resolve(tempKey);
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(p.toFile(), true))) {
            out.write(bytes);
        }
    }

    @Override
    public String finalizeUpload(String tempKey, String finalKey) throws IOException {
        Path src = root.resolve(tempKey);
        Path dst = root.resolve("store").resolve(finalKey);
        Files.createDirectories(dst.getParent());
        Files.move(src, dst);
        return "store/" + finalKey;
    }

    @Override
    public InputStream readRange(String key, long start, long end) throws IOException {
        File f = root.resolve(key).toFile();
        RandomAccessFile raf = new RandomAccessFile(f, "r");
        raf.seek(start);
        long length = end - start + 1;
        InputStream is = new BufferedInputStream(new FileInputStream(raf.getFD())) {
            long remaining = length;
            @Override public int read() throws IOException {
                if (remaining <= 0) return -1;
                int b = super.read();
                if (b >= 0) remaining--;
                return b;
            }
            @Override public int read(byte[] b, int off, int len) throws IOException {
                if (remaining <= 0) return -1;
                len = (int)Math.min(len, remaining);
                int n = super.read(b, off, len);
                if (n > 0) remaining -= n;
                return n;
            }
        };
        return is;
    }

    @Override
    public long size(String key) throws IOException {
        return Files.size(root.resolve(key));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(root.resolve(key));
    }
}