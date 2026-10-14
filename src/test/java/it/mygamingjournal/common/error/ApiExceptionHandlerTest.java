package it.mygamingjournal.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ErrorTestController.class)
@AutoConfigureMockMvc(addFilters = false)
class ApiExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void notFoundBecomesProblemDetail404() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Thing not found"));
    }

    @Test
    void conflictBecomes409() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Thing already exists"));
    }

    @Test
    void badRequestBecomes400() throws Exception {
        mockMvc.perform(get("/test/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Thing is wrong"));
    }

    @Test
    void unexpectedErrorBecomesGeneric500ProblemDetail() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Unexpected error"));
    }

    @Test
    void invalidBodyListsFieldErrors() throws Exception {
        mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void pagingDefaultsToFirstPageOfTwenty() throws Exception {
        mockMvc.perform(get("/test/paged"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(20));
    }

    @Test
    void pageSizeAboveFortyIsRejected() throws Exception {
        mockMvc.perform(get("/test/paged").param("pageSize", "41"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("pageSize"));
    }

    @Test
    void pageZeroIsRejected() throws Exception {
        mockMvc.perform(get("/test/paged").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("page"));
    }
}
