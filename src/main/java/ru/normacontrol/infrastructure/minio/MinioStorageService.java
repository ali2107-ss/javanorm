package ru.normacontrol.infrastructure.minio;

import io.minio.*;

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
     * Upload raw bytes to MinIO.
     *
     * @param objectKey object key
     * @param bytes file contents
     * @param contentType content type
     */
    public void upload(String objectKey, byte[] bytes, String contentType) {
        try {
            ensureBucketExists();

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .contentType(contentType)
                    .build());

            log.info("Р¤Р°Р№Р» Р·Р°РіСЂСѓР¶РµРЅ РІ MinIO: {}", objectKey);
        } catch (Exception e) {
            log.error("РћС€РёР±РєР° Р·Р°РіСЂСѓР·РєРё Р±Р°Р№С‚РѕРІ РІ MinIO: {}", e.getMessage(), e);
            throw new RuntimeException("РћС€РёР±РєР° Р·Р°РіСЂСѓР·РєРё С„Р°Р№Р»Р° РІ С…СЂР°РЅРёР»РёС‰Рµ", e);
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
     * Download an object into a byte array.
     *
     * @param objectKey object key
     * @return file bytes
     */
    public byte[] downloadBytes(String objectKey) {
        try (InputStream inputStream = downloadFile(objectKey)) {
            return inputStream.readAllBytes();
        } catch (Exception e) {
            log.error("РћС€РёР±РєР° С‡С‚РµРЅРёСЏ Р±Р°Р№С‚РѕРІ РёР· MinIO: {}", e.getMessage(), e);
            throw new RuntimeException("РћС€РёР±РєР° С‡С‚РµРЅРёСЏ С„Р°Р№Р»Р° РёР· С…СЂР°РЅРёР»РёС‰Р°", e);
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
