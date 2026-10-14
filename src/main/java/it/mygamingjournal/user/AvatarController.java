package it.mygamingjournal.user;

import it.mygamingjournal.auth.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class AvatarController {

    private final AvatarService avatars;

    @PutMapping(path = "/api/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AvatarResponse updateAvatar(@AuthenticationPrincipal AuthUser me, @RequestParam("file") MultipartFile file) {
        return avatars.updateAvatar(me.id(), file);
    }
}
