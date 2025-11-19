package com.example.DockerCloudMAGA.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ByteArrayResource;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

public class DrawVisionService {
    public byte[] DrawSquareCV(ByteArrayResource resource, String jsonResponse) {
        try {
            BufferedImage img = ImageIO.read(resource.getInputStream());
            Graphics2D g = img.createGraphics();

            g.setColor(Color.RED);
            g.setStroke(new BasicStroke(2));
            g.setFont(new Font("Arial", Font.BOLD, 16));

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonResponse);

            JsonNode labels = root.path("body")
                    .path("multiobject_labels")
                    .get(0)
                    .path("labels");

            for (JsonNode label : labels) {
                JsonNode coords = label.path("coord");
                int x1 = coords.get(0).asInt();
                int y1 = coords.get(1).asInt();
                int x2 = coords.get(2).asInt();
                int y2 = coords.get(3).asInt();

                String text = label.path("rus").asText();

                g.drawRect(x1, y1, x2 - x1, y2 - y1);
                g.drawString(text, x1, Math.max(y1 - 5, 15));
            }

            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Ошибка при обработки ответа от CV", e);
        }
    }
}
