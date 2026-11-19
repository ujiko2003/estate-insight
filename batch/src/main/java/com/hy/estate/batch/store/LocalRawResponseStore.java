package com.hy.estate.batch.store;

import com.hy.estate.batch.config.CollectProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class LocalRawResponseStore implements RawResponseStore {

    private final Path baseDir;

    public LocalRawResponseStore(CollectProperties collectProperties) {
        this.baseDir = Path.of(collectProperties.rawDir());
    }

    @Override
    public void save(String key, String content) {
        Path file = baseDir.resolve(key);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("원본 저장 실패 : "+ key,e);
        }
    }
}
