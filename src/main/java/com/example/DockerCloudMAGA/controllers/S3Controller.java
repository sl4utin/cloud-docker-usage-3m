package com.example.DockerCloudMAGA.controllers;

import com.example.DockerCloudMAGA.service.S3Service;
import com.example.DockerCloudMAGA.service.DrawVisionService;
import com.example.DockerCloudMAGA.utils.ByteArrayMultipartFile;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import org.springframework.web.client.RestTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;

@Controller
@RequestMapping("/s3")
public class S3Controller {
    @Autowired
    private S3Service s3Service;

    @Value("${computer.vision.token}")
    private String tokenCV;

    @GetMapping("/buckets")
    public String listBuckets(Model model) {
        model.addAttribute("buckets", s3Service.listBuckets());
        return "buckets";
    }

    @PostMapping("/buckets/create")
    public String createBucket(@RequestParam String name) {
        s3Service.createBucket(name);
        return "redirect:/s3/buckets";
    }

    @PostMapping("/buckets/delete")
    public String deleteBucket(@RequestParam String name) {
        s3Service.deleteBucket(name);
        return "redirect:/s3/buckets";
    }

    @GetMapping("/{bucketName}/objects")
    public String listObjects(@PathVariable String bucketName, Model model) {
        model.addAttribute("bucketName", bucketName);
        model.addAttribute("objects", s3Service.listObjects(bucketName));
        return "objects";
    }

    @PostMapping("/{bucketName}/upload")
    public String upload(@PathVariable String bucketName, @RequestParam MultipartFile file) throws IOException {
        s3Service.uploadFile(bucketName, file);
        return "redirect:/s3/" + bucketName + "/objects";
    }

    @PostMapping("/{bucketName}/delete")
    public String delete(@PathVariable String bucketName, @RequestParam String key) {
        s3Service.deleteFile(bucketName, key);
        return "redirect:/s3/" + bucketName + "/objects";
    }

    @GetMapping("/{bucketName}/file/{key}")
    public void openFile(
            @PathVariable String bucketName,
            @PathVariable String key,
            HttpServletResponse response) throws IOException {

        HeadObjectResponse objectResponse = s3Service.getObjectMetadata(bucketName, key);
        byte[] content = s3Service.downloadFile(bucketName, key);

        String contentType = objectResponse.contentType();
        if (contentType == null || contentType.isEmpty()) {
            if (key.toLowerCase().endsWith(".txt") || key.toLowerCase().endsWith(".log")) {
                contentType = "text/plain";
            } else if (key.toLowerCase().matches(".*\\.(jpg|jpeg|png|gif|bmp|svg)$")) {
                contentType = "image/*";
            } else {
                contentType = "application/octet-stream";
            }
        }

        response.setContentType(contentType);
        response.setHeader("Content-Disposition", "inline; filename=\"" + key + "\"");
        response.getOutputStream().write(content);
        response.flushBuffer();
    }

    @PostMapping("/{bucketName}/vision/{key}")
    public String computerVision(
            @PathVariable String bucketName,
            @PathVariable String key,
            HttpServletResponse response) {
        try {
            byte[] fileBytes = s3Service.downloadFile(bucketName, key);

            String cvJson = s3Service.computerVision(fileBytes, key);

            DrawVisionService drawUtils = new DrawVisionService();
            org.springframework.core.io.ByteArrayResource resource = new org.springframework.core.io.ByteArrayResource(fileBytes);
            byte[] drawnBytes = drawUtils.DrawSquareCV(resource, cvJson);

            String newKey = key.substring(0, key.lastIndexOf('.')) + "-with-cv.png";
            MultipartFile newFile = new ByteArrayMultipartFile(
                    drawnBytes,
                    "file",
                    newKey,
                    "image/png"
            );
            s3Service.uploadFile(bucketName, newFile);

            byte[] result = s3Service.downloadFile(bucketName, newKey);
            response.setContentType("image/png");
            response.setHeader("Content-Disposition", "inline; filename=\"" + newKey + "\"");
            response.getOutputStream().write(result);
            response.flushBuffer();
            return null;

        } catch (Exception e) {
            throw new RuntimeException("Ошибка компьютерного зрения для файла " + key, e);
        }
    }
}