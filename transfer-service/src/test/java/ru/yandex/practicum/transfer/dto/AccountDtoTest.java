package ru.yandex.practicum.transfer.dto;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class AccountDtoTest {
    @Test
    void shouldCreateAccountDto() {
        AccountDto dto = new AccountDto(
                1L,
                "ivanov",
                "Иван Иванов",
                LocalDate.of(1990, 1, 15),
                5000L
        );
        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.login()).isEqualTo("ivanov");
        assertThat(dto.name()).isEqualTo("Иван Иванов");
        assertThat(dto.birthdate()).isEqualTo(LocalDate.of(1990, 1, 15));
        assertThat(dto.balance()).isEqualTo(5000L);
    }
}
