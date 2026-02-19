package ru.yandex.practicum.mybankfront.controller.dto;

import org.junit.jupiter.api.Test;
import ru.yandex.practicum.mybankfront.dto.CashAction;

import static org.assertj.core.api.Assertions.assertThat;

class CashActionTest {
    @Test
    void shouldHavePutAndGetValues() {
        assertThat(CashAction.values()).containsExactly(CashAction.PUT, CashAction.GET);
    }
    @Test
    void shouldConvertToStringCorrectly() {
        assertThat(CashAction.PUT.toString()).isEqualTo("PUT");
        assertThat(CashAction.GET.toString()).isEqualTo("GET");
    }
    @Test
    void shouldParseFromString() {
        assertThat(CashAction.valueOf("PUT")).isEqualTo(CashAction.PUT);
        assertThat(CashAction.valueOf("GET")).isEqualTo(CashAction.GET);
    }
}
