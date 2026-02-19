package ru.yandex.practicum.mybankfront.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mybankfront.config.TestSecurityConfig;
import ru.yandex.practicum.mybankfront.dto.CashAction;
import ru.yandex.practicum.mybankfront.service.AccountService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(controllers = MainController.class)
@Import(TestSecurityConfig.class)
class MainControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService accountService;
    @Test
    @WithMockUser(username = "ivanov")
    void shouldRedirectIndexToAccount() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account"));
    }
    @Test
    @WithMockUser(username = "ivanov")
    void shouldGetAccountAndCallService() throws Exception {
        Map<String, Object> accountData = Map.of(
                "name", "Иван Иванов",
                "sum", 5000,
                "accounts", List.of()
        );
        when(accountService.getAccountInfo(anyString(), anyString()))
                .thenReturn(Mono.just(accountData));
        mockMvc.perform(get("/account"))
                .andExpect(request().asyncStarted());
        verify(accountService).getAccountInfo(eq("ivanov"), anyString());
    }
    @Test
    @WithMockUser(username = "ivanov")
    void shouldUpdateAccountAndCallService() throws Exception {
        Map<String, Object> updatedData = Map.of(
                "name", "Иван Петрович Иванов",
                "sum", 5000,
                "accounts", List.of()
        );
        when(accountService.updateAccount(anyString(), anyString(), any(LocalDate.class), anyString()))
                .thenReturn(Mono.just(updatedData));
        mockMvc.perform(post("/account")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Иван Петрович Иванов")
                        .param("birthdate", "1990-01-15"))
                .andExpect(request().asyncStarted());
        verify(accountService).updateAccount(eq("ivanov"), eq("Иван Петрович Иванов"), eq(LocalDate.of(1990, 1, 15)), anyString());
    }
    @Test
    @WithMockUser(username = "ivanov")
    void shouldProcessCashAndCallService() throws Exception {
        Map<String, Object> responseData = Map.of(
                "name", "Иван Иванов",
                "sum", 5500,
                "accounts", List.of()
        );
        when(accountService.processCash(anyString(), anyInt(), any(CashAction.class), anyString()))
                .thenReturn(Mono.just(responseData));
        mockMvc.perform(post("/cash")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("value", "500")
                        .param("action", "PUT"))
                .andExpect(request().asyncStarted());
        verify(accountService).processCash(eq("ivanov"), eq(500), eq(CashAction.PUT), anyString());
    }
    @Test
    @WithMockUser(username = "ivanov")
    void shouldProcessWithdrawAndCallService() throws Exception {
        Map<String, Object> responseData = Map.of(
                "name", "Иван Иванов",
                "sum", 4500,
                "accounts", List.of()
        );
        when(accountService.processCash(anyString(), anyInt(), any(CashAction.class), anyString()))
                .thenReturn(Mono.just(responseData));
        mockMvc.perform(post("/cash")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("value", "500")
                        .param("action", "GET"))
                .andExpect(request().asyncStarted());
        verify(accountService).processCash(eq("ivanov"), eq(500), eq(CashAction.GET), anyString());
    }
    @Test
    @WithMockUser(username = "ivanov")
    void shouldTransferMoneyAndCallService() throws Exception {
        Map<String, Object> responseData = Map.of(
                "name", "Иван Иванов",
                "sum", 4900,
                "accounts", List.of("petrov", "sidorov")
        );
        when(accountService.transfer(anyString(), anyInt(), anyString(), anyString()))
                .thenReturn(Mono.just(responseData));
        mockMvc.perform(post("/transfer")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("value", "100")
                        .param("login", "petrov"))
                .andExpect(request().asyncStarted());
        verify(accountService).transfer(eq("ivanov"), eq(100), eq("petrov"), anyString());
    }
    @Test
    void shouldRequireAuthenticationForAccount() throws Exception {
        mockMvc.perform(get("/account"))
                .andExpect(status().isForbidden());
    }
    @Test
    void shouldRequireAuthenticationForAccountPost() throws Exception {
        mockMvc.perform(post("/account")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Test")
                        .param("birthdate", "2000-01-01"))
                .andExpect(status().isForbidden());
    }
    @Test
    void shouldRequireAuthenticationForCash() throws Exception {
        mockMvc.perform(post("/cash")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("value", "500")
                        .param("action", "PUT"))
                .andExpect(status().isForbidden());
    }
    @Test
    void shouldRequireAuthenticationForTransfer() throws Exception {
        mockMvc.perform(post("/transfer")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("value", "100")
                        .param("login", "petrov"))
                .andExpect(status().isForbidden());
    }
}
