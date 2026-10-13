package com.mycompany.applicationone;

import com.mycompany.applicationone.controller.GreetingsController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GreetingsControllerTests {
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private MockMvc mvc;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        mvc = MockMvcBuilders.standaloneSetup(new GreetingsController(redis, "test-container")).build();
    }

    @Test void pageIncrementsAndShowsContainer() throws Exception {
        when(values.increment("hits")).thenReturn(42L);
        mvc.perform(get("/")).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(content().string(containsString("Bonjour ! Cette page a été vue 42 fois.")))
            .andExpect(content().string(containsString("test-container")));
        verify(values, times(1)).increment("hits");
    }

    @Test void jsonVisitsIncrementOnEveryRequest() throws Exception {
        when(values.increment("hits")).thenReturn(1L, 2L);
        mvc.perform(get("/api/visits")).andExpect(jsonPath("$.hits").value(1));
        mvc.perform(get("/api/visits")).andExpect(jsonPath("$.hits").value(2))
            .andExpect(jsonPath("$.container").value("test-container"));
        verify(values, times(2)).increment("hits");
    }

    @Test void healthDoesNotIncrement() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(content().string("UP"));
        verify(redis).hasKey("hits");
        verifyNoInteractions(values);
    }

    @Test void redisFailureReturns503() throws Exception {
        when(values.increment("hits")).thenThrow(new RedisConnectionFailureException("offline"));
        mvc.perform(get("/")).andExpect(status().isServiceUnavailable())
            .andExpect(header().string("Retry-After", "3"));
    }

    @Test void healthDetectsRedisFailure() throws Exception {
        when(redis.hasKey("hits")).thenThrow(new RedisConnectionFailureException("offline"));
        mvc.perform(get("/health")).andExpect(status().isServiceUnavailable());
    }

    @Test void originalTutorialRouteStillWorks() throws Exception {
        when(values.increment("hits")).thenReturn(3L);
        mvc.perform(get("/api/v1/welcome")).andExpect(status().isOk())
            .andExpect(content().string(containsString("vue 3 fois")));
    }
}
