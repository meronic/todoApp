package com.youmh.taskflow.controller;

import com.youmh.taskflow.dto.TodoCreateDto;
import com.youmh.taskflow.entity.Todo;
import com.youmh.taskflow.entity.TodoGroup;
import com.youmh.taskflow.service.TodoService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/todos")
public class TodoApiController {

    private final TodoService todoService;

    public TodoApiController(TodoService todoService) {
        this.todoService = todoService;
    }

    @GetMapping
    public List<Todo> findAll() {
        return todoService.findAll();
    }

    @PostMapping
    public Todo create(@Valid @RequestBody TodoCreateDto request) {
        return todoService.create(request.getTitle());
    }

    @PutMapping("/{id}")
    public Todo updateTitle(@PathVariable Long id, @Valid @RequestBody TodoCreateDto request) {
        return todoService.updateTitle(id, request.getTitle());
    }

    @PutMapping("/{id}/group")
    public Todo updateGroup(@PathVariable Long id, @RequestParam TodoGroup groupType) {
        return todoService.updateGroup(id, groupType);
    }

    @PutMapping("/{id}/due-date")
    public Todo updateDueDate(@PathVariable Long id, @RequestParam LocalDate dueDate) {
        return todoService.updateDueDate(id, dueDate);
    }

    @DeleteMapping("/{id}/due-date")
    public Todo clearDueDate(@PathVariable Long id) {
        return todoService.clearDueDate(id);
    }

    @PutMapping("/{id}/toggle")
    public Todo toggleCompleted(@PathVariable Long id) {
        return todoService.toggleCompleted(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        todoService.delete(id);
    }
}
