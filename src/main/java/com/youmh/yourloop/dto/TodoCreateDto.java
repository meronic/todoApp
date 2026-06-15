package com.youmh.yourloop.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TodoCreateDto {

    @NotBlank(message = "???쇱쓣 ?낅젰?댁＜?몄슂.")
    private String title;
}
