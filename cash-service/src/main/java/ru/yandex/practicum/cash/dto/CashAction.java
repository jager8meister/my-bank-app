package ru.yandex.practicum.cash.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Type of cash operation: PUT deposits money into the account, GET withdraws money from the account")
public enum CashAction {

    PUT, GET
}
