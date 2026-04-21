package ru.normacontrol.infrastructure.minio;

import io.minio.*;
import io.minio.errors.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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

    /**
     * Загрузить файл в MinIO.
     */
    public void uploadFile(String objectKey, MultipartFile file) {
        try {
            ensureBucketExists();

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectKey)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());

            log.info("Файл загружен в MinIO: {}", objectKey);

        } catch (Exception e) {
            log.error("Ошибка загрузки файла в MinIO: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка загрузки файла в хранилище", e);
        }
    }

    /**
     * Скачать файл из MinIO.
     */
    public InputStream downloadFile(String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            log.error("Ошибка скачивания файла из MinIO: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка скачивания файла из хранилища", e);
        }
    }

    /**
     * Удалить файл из MinIO.
     */
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

    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .build());
        if (!exists) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .build());
            log.info("Создан bucket MinIO: {}", minioConfig.getBucketName());
        }
    }
}
