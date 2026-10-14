package it.mygamingjournal.common.error;

import it.mygamingjournal.common.paging.PageParams;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
class ErrorTestController {

    record Body(@NotBlank String name) {
    }

    @GetMapping("/not-found")
    void notFound() {
        throw new NotFoundException("Thing not found");
    }

    @GetMapping("/conflict")
    void conflict() {
        throw new ConflictException("Thing already exists");
    }

    @GetMapping("/bad-request")
    void badRequest() {
        throw new BadRequestException("Thing is wrong");
    }

    @PostMapping("/body")
    void body(@Valid @RequestBody Body body) {
    }

    @GetMapping("/paged")
    PageParams paged(@Valid PageParams paging) {
        return paging;
    }
}
