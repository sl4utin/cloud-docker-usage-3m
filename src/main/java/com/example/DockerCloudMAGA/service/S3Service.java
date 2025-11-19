package com.example.DockerCloudMAGA.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.List;

@Service
public class S3Service {
    @Autowired
    private S3Client s3;

    @Value("${computer.vision.token}")
    private String tokenCV;

    public List<Bucket> listBuckets() {
        return s3.listBuckets().buckets();
    }

    public void createBucket(String name) {
        s3.createBucket(CreateBucketRequest.builder().bucket(name).build());
    }

    public void deleteBucket(String name) {
        s3.deleteBucket(DeleteBucketRequest.builder().bucket(name).build());
    }

    public List<S3Object> listObjects(String bucketName) {
        return s3.listObjectsV2(ListObjectsV2Request.builder().bucket(bucketName).build()).contents();
    }

    public void uploadFile(String bucketName, MultipartFile file) throws IOException {
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(file.getOriginalFilename())
                        .contentType(file.getContentType())
                        .build(),
                RequestBody.fromBytes(file.getBytes()));
    }

    public void deleteFile(String bucketName, String key) {
        s3.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build());
    }

    public HeadObjectResponse getObjectMetadata(String bucketName, String key) {
        return s3.headObject(HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build());
    }

    public byte[] downloadFile(String bucketName, String key) throws IOException {
        return s3.getObject(GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .build(),
                ResponseTransformer.toBytes()).asByteArray();
    }

    public String computerVision(byte[] fileBytes, String fileName) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = "https://smarty.mail.ru/api/v1/objects/detect?oauth_token=" + tokenCV + "&oauth_provider=mcs";

            MultiValueMap<String, Object> parts = getStringObjectMultiValueMap(fileBytes, fileName);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(parts, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            return response.getBody();

        } catch (Exception e) {
            throw new RuntimeException("Ошибка при отправке на компьютерное зрение для файла " + fileName, e);
        }
    }

    private static MultiValueMap<String, Object> getStringObjectMultiValueMap(byte[] fileBytes, String fileName) {
        ByteArrayResource resource = new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", resource);
        parts.add("meta", """
                {
                  "mode": ["multiobject"],
                  "images": [{"name": "file"}]
                }
                """);
        return parts;
    }
}