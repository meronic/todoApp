package com.youmh.taskflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TodoCreateDto {

    @NotBlank(message = "할 일을 입력해주세요.")
    private String title;
}
