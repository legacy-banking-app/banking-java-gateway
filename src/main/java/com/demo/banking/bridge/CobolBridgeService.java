package com.demo.banking.bridge;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

@Service
public class CobolBridgeService {

    @Value("${cobol.bridge.file-path}")
    private String basePath;

    public void appendToInputFile(String filename, String record) throws IOException {
        Path path = Paths.get(basePath, filename);
        Files.writeString(path, record + System.lineSeparator(),
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public List<String> readOutputFile(String filename) throws IOException {
        Path path = Paths.get(basePath, filename);
        if (!Files.exists(path)) return List.of();
        return Files.readAllLines(path);
    }
}
