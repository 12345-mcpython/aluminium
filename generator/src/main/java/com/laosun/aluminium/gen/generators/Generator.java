package com.laosun.aluminium.gen.generators;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public abstract class Generator<T, R> {
    protected static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Type listType;

    protected Generator() {
        Type[] typeArguments = ((ParameterizedType) getClass()
            .getGenericSuperclass()).getActualTypeArguments();
        this.listType = TypeToken.getParameterized(List.class, typeArguments[0]).getType();
    }

    public final R generate(String dataDir) throws IOException {
        Path inputPath = resolveInputPath(dataDir);
        Path outputPath = resolveOutputPath(dataDir);

        List<T> rawList = readRawData(inputPath);

        R processedData = processData(rawList);

        writeResult(outputPath, processedData);
        return processedData;
    }

    protected abstract Path resolveInputPath(String projectDir);
    protected abstract Path resolveOutputPath(String projectDir);
    protected abstract R processData(List<T> rawList);

    private List<T> readRawData(Path inputPath) throws IOException {
        return GSON.fromJson(Files.readString(inputPath), listType);
    }

    private void writeResult(Path outputPath, R data) throws IOException {
        Files.createDirectories(outputPath.getParent());
        Files.write(outputPath, GSON.toJson(data).getBytes());
    }
}