package it.mygamingjournal.library;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final LibraryService library;
    private final PlayAvailabilityRepository availabilities;

    @Transactional
    public AvailabilityResponse set(long userId, long rawgGameId, AvailabilityRequest request) {
        GameEntry entry = library.requireEntry(userId, rawgGameId);
        PlayAvailability availability = availabilities.findById(entry.getId())
                .orElseGet(() -> new PlayAvailability(entry.getId()));
        availability.setPlatforms(new ArrayList<>(request.platforms().stream().distinct().toList()));
        availability.setLanguages(new ArrayList<>(request.languages().stream().distinct().toList()));
        availability.setAvailableUntil(request.availableUntil());
        return AvailabilityResponse.from(availabilities.save(availability));
    }

    @Transactional
    public void clear(long userId, long rawgGameId) {
        availabilities.deleteById(library.requireEntry(userId, rawgGameId).getId());
    }
}
