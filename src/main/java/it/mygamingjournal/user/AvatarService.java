package it.mygamingjournal.user;

import it.mygamingjournal.common.error.BadRequestException;
import it.mygamingjournal.media.ImageSignatures;
import it.mygamingjournal.media.ImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class AvatarService {

    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final ProfileService profiles;
    private final ImageStorage storage;

    @Transactional
    public AvatarResponse updateAvatar(long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Avatar file is required");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("Avatar must be at most 5 MB");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Avatar file could not be read");
        }
        if (!ImageSignatures.isSupported(content)) {
            throw new BadRequestException("Avatar must be a JPEG, PNG or WebP image");
        }
        User user = profiles.requireUser(userId);
        String url = storage.uploadAvatar(userId, content);
        user.setAvatarUrl(url);
        return new AvatarResponse(url);
    }
}
