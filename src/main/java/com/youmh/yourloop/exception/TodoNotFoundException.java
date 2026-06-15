package com.youmh.yourloop.exception;

public class TodoNotFoundException extends RuntimeException {

    public TodoNotFoundException(Long id) {
        super("Todo瑜?李얠쓣 ???놁뒿?덈떎. id=" + id);
    }
}
