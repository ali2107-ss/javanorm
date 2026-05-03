package ru.normacontrol.infrastructure.minio;

import io.minio.*;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Сервис хранения файлов в MinIO (S3-совместимое хранилище).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    @PostConstruct
    public void init() {
        try {
            boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder()
                    .bucket(minioConfig.getBucketName()).build());
            if (!exists) {
                minioClient.makeBucket(
                    MakeBucketArgs.builder()
                        .bucket(minioConfig.getBucketName()).build());
                log.info("Bucket создан: {}", minioConfig.getBucketName());
            }
        } catch (Exception e) {
            log.error("Ошибка инициализации MinIO: {}", e.getMessage());
        }
    }

    // Для совместимости со старым кодом
    public void uploadFile(String objectKey, MultipartFile file) {
        try {
            upload(objectKey, file.getInputStream(), file.getContentType());
        } catch (Exception e) {
            throw new RuntimeException("Ошибка загрузки файла", e);
        }
    }

    public String upload(String path, InputStream stream, String contentType) {
        try {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(path)
                    .stream(stream, -1, 10485760)
                    .contentType(contentType)
                    .build());
            log.info("Файл загружен в MinIO: {}", path);
            return path;
        } catch (Exception e) {
            log.error("Ошибка загрузки в MinIO: {}", e.getMessage());
            throw new RuntimeException("Не удалось сохранить файл: " + e.getMessage());
        }
    }

    public void uploadBytes(String path, byte[] bytes, String contentType) {
        upload(path, new ByteArrayInputStream(bytes), contentType);
    }

    public void upload(String path, byte[] bytes, String contentType) {
        upload(path, new ByteArrayInputStream(bytes), contentType);
    }

    public InputStream download(String path) {
        try {
            return minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(path)
                    .build());
        } catch (Exception e) {
            log.error("Ошибка скачивания из MinIO: {}", e.getMessage());
            throw new RuntimeException("Файл не найден: " + path);
        }
    }

    public InputStream downloadFile(String path) {
        return download(path);
    }

    public byte[] downloadBytes(String path) {
        try (InputStream stream = download(path)) {
            return stream.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка чтения файла: " + path);
        }
    }

    public void deleteFile(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectKey)
                    .build());
            log.info("Файл удалён из MinIO: {}", objectKey);
        } catch (Exception e) {
            log.error("Ошибка удаления файла из MinIO: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка удаления файла из хранилища", e);
        }
    }
}
