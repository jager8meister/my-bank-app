package ru.yandex.practicum.mybankfront.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mybankfront.service.AccountService;
import ru.yandex.practicum.mybankfront.service.RegistrationService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = RegistrationController.class)
@Import(RegistrationControllerTest.RegistrationTestSecurityConfig.class)
class RegistrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegistrationService registrationService;

    @MockitoBean
    private AccountService accountService;

    @MockitoBean
    private WebClient webClient;

    @TestConfiguration
    @EnableWebSecurity
    static class RegistrationTestSecurityConfig {

        @Bean
        @Primary
        public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .anyRequest().permitAll()
                    );
            return http.build();
        }
    }

    private MvcResult performRegisterPost(String login, String password, String confirmPassword,
                                          String name, String birthdate) throws Exception {
        MvcResult asyncResult = mockMvc.perform(post("/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("login", login)
                        .param("password", password)
                        .param("confirmPassword", confirmPassword)
                        .param("name", name)
                        .param("birthdate", birthdate))
                .andExpect(request().asyncStarted())
                .andReturn();
        return mockMvc.perform(asyncDispatch(asyncResult)).andReturn();
    }

    @Test
    void shouldReturnRegisterTemplateOnGet() throws Exception {
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"));
    }

    @Test
    void shouldRedirectOnSuccessfulRegistration() throws Exception {
        when(registrationService.register(any())).thenReturn(Mono.empty());

        mockMvc.perform(asyncDispatch(
                        mockMvc.perform(post("/register")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                        .param("login", "newuser")
                                        .param("password", "password123")
                                        .param("confirmPassword", "password123")
                                        .param("name", "Новый Пользователь")
                                        .param("birthdate", "1990-01-15"))
                                .andExpect(request().asyncStarted())
                                .andReturn()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered=true"));
    }

    @Test
    void shouldReturnRegisterViewWithErrorOnTooShortLogin() throws Exception {
        MvcResult asyncResult = mockMvc.perform(post("/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("login", "ab")
                        .param("password", "password123")
                        .param("confirmPassword", "password123")
                        .param("name", "Иван Иванов")
                        .param("birthdate", "1990-01-15"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("error"));

        verify(registrationService, never()).register(any());
    }

    @Test
    void shouldReturnRegisterViewWithErrorOnTooLongLogin() throws Exception {
        String longLogin = "a".repeat(21);

        MvcResult asyncResult = mockMvc.perform(post("/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("login", longLogin)
                        .param("password", "password123")
                        .param("confirmPassword", "password123")
                        .param("name", "Иван Иванов")
                        .param("birthdate", "1990-01-15"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("error"));

        verify(registrationService, never()).register(any());
    }

    @Test
    void shouldReturnRegisterViewWithErrorOnInvalidLoginCharacters() throws Exception {
        MvcResult asyncResult = mockMvc.perform(post("/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("login", "Ivan123")
                        .param("password", "password123")
                        .param("confirmPassword", "password123")
                        .param("name", "Иван Иванов")
                        .param("birthdate", "1990-01-15"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("error"));

        verify(registrationService, never()).register(any());
    }

    @Test
    void shouldReturnRegisterViewWithErrorOnPasswordMismatch() throws Exception {
        MvcResult asyncResult = mockMvc.perform(post("/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("login", "ivanov")
                        .param("password", "password123")
                        .param("confirmPassword", "differentpassword")
                        .param("name", "Иван Иванов")
                        .param("birthdate", "1990-01-15"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("error"));

        verify(registrationService, never()).register(any());
    }

    @Test
    void shouldReturnRegisterViewWithErrorOnTooShortPassword() throws Exception {
        MvcResult asyncResult = mockMvc.perform(post("/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("login", "ivanov")
                        .param("password", "abc")
                        .param("confirmPassword", "abc")
                        .param("name", "Иван Иванов")
                        .param("birthdate", "1990-01-15"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("error"));

        verify(registrationService, never()).register(any());
    }

    @Test
    void shouldReturnRegisterViewWithErrorWhenUnder18() throws Exception {
        String recentBirthdate = java.time.LocalDate.now().minusYears(17).toString();

        MvcResult asyncResult = mockMvc.perform(post("/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("login", "ivanov")
                        .param("password", "password123")
                        .param("confirmPassword", "password123")
                        .param("name", "Иван Иванов")
                        .param("birthdate", recentBirthdate))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("error"));

        verify(registrationService, never()).register(any());
    }

    @Test
    void shouldReturnRegisterViewWithErrorOnBlankName() throws Exception {
        MvcResult asyncResult = mockMvc.perform(post("/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("login", "ivanov")
                        .param("password", "password123")
                        .param("confirmPassword", "password123")
                        .param("name", "   ")
                        .param("birthdate", "1990-01-15"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("error"));

        verify(registrationService, never()).register(any());
    }

    @Test
    void shouldReturnRegisterViewWithErrorWhenServiceThrows() throws Exception {
        when(registrationService.register(any()))
                .thenReturn(Mono.error(new RuntimeException("Логин уже занят")));

        MvcResult asyncResult = mockMvc.perform(post("/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("login", "ivanov")
                        .param("password", "password123")
                        .param("confirmPassword", "password123")
                        .param("name", "Иван Иванов")
                        .param("birthdate", "1990-01-15"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("error"));
    }

    @Test
    void shouldPreserveLoginAndNameInModelOnValidationError() throws Exception {
        MvcResult asyncResult = mockMvc.perform(post("/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("login", "ab")
                        .param("password", "password123")
                        .param("confirmPassword", "password123")
                        .param("name", "Иван Иванов")
                        .param("birthdate", "1990-01-15"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attribute("login", "ab"))
                .andExpect(model().attribute("name", "Иван Иванов"));
    }
}
