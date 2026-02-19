package ru.yandex.practicum.accounts.model;

import jakarta.validation.constraints.AssertTrue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Table(name = "accounts", schema = "accounts_schema")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    @Id
    private Long id;

    @Version
    @Column("version")
    private Long version;

    @Column("login")
    private String login;

    @Column("name")
    private String name;

    @Column("birthdate")
    private LocalDate birthdate;

    @Column("balance")
    private Integer balance;

    @AssertTrue(message = "Age must be at least 18 years")
    public boolean isBirthdateValid() {
        return birthdate == null || ChronoUnit.YEARS.between(birthdate, LocalDate.now()) >= 18;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Account account = (Account) o;
        return Objects.equals(login, account.login);
    }

    @Override
    public int hashCode() {
        return Objects.hash(login);
    }
}
