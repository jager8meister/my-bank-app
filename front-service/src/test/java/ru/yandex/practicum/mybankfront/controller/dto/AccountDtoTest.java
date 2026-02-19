package ru.yandex.practicum.mybankfront.controller.dto;

import org.junit.jupiter.api.Test;
import ru.yandex.practicum.mybankfront.dto.AccountDto;

import static org.assertj.core.api.Assertions.assertThat;

class AccountDtoTest {
    @Test
    void shouldCreateAccountDto() {
        AccountDto dto = new AccountDto("ivanov", "Иван Иванов");
        assertThat(dto.login()).isEqualTo("ivanov");
        assertThat(dto.name()).isEqualTo("Иван Иванов");
    }
    @Test
    void shouldCompareAccountDtos() {
        AccountDto dto1 = new AccountDto("ivanov", "Иван Иванов");
        AccountDto dto2 = new AccountDto("ivanov", "Иван Иванов");
        AccountDto dto3 = new AccountDto("petrov", "Петр Петров");
        assertThat(dto1).isEqualTo(dto2);
        assertThat(dto1).isNotEqualTo(dto3);
        assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
    }
    @Test
    void shouldConvertToString() {
        AccountDto dto = new AccountDto("ivanov", "Иван Иванов");
        String result = dto.toString();
        assertThat(result).contains("ivanov");
        assertThat(result).contains("Иван Иванов");
    }
}
