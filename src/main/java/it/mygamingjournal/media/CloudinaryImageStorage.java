package it.mygamingjournal.media;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class CloudinaryImageStorage implements ImageStorage {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryImageStorage.class);

    private final Cloudinary cloudinary;

    public CloudinaryImageStorage(@Value("${app.cloudinary.url:}") String cloudinaryUrl) {
        this.cloudinary = cloudinaryUrl == null || cloudinaryUrl.isBlank() ? null : new Cloudinary(cloudinaryUrl);
    }

    @Override
    public String uploadAvatar(long userId, byte[] content) {
        if (cloudinary == null) {
            throw new MediaUnavailableException("Image upload is not configured");
        }
        try {
            Map<?, ?> result = cloudinary.uploader().upload(content, ObjectUtils.asMap(
                    "folder", "mygamingjournal/avatars",
                    "public_id", "user-" + userId,
                    "overwrite", true,
                    "resource_type", "image"));
            return (String) result.get("secure_url");
        } catch (IOException | RuntimeException e) {
            log.warn("Avatar upload failed for user {}: {}", userId, e.getClass().getSimpleName());
            throw new MediaUnavailableException("Image upload failed");
        }
    }
}
