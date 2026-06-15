package com.youmh.yourloop.service;

import com.youmh.yourloop.entity.Todo;
import com.youmh.yourloop.entity.TodoGroup;
import com.youmh.yourloop.exception.TodoNotFoundException;
import com.youmh.yourloop.repository.TodoRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TodoService {

    private final TodoRepository todoRepository;

    public TodoService(TodoRepository todoRepository) {
        this.todoRepository = todoRepository;
    }

    public List<Todo> findAll() {
        return todoRepository.findAllByOrderByIdDesc();
    }

    public Todo create(String title) {
        Todo todo = new Todo();
        todo.setTitle(normalizeTitle(title));
        todo.setCompleted(false);
        todo.setGroupType(TodoGroup.UNCATEGORIZED);

        return todoRepository.save(todo);
    }

    public Todo updateTitle(Long id, String title) {
        Todo todo = findById(id);
        todo.setTitle(normalizeTitle(title));

        return todoRepository.save(todo);
    }

    public Todo updateGroup(Long id, TodoGroup groupType) {
        Todo todo = findById(id);
        todo.setGroupType(groupType);

        return todoRepository.save(todo);
    }

    public Todo updateDueDate(Long id, LocalDate dueDate) {
        Todo todo = findById(id);
        todo.setDueDate(dueDate);

        return todoRepository.save(todo);
    }

    public Todo clearDueDate(Long id) {
        Todo todo = findById(id);
        todo.setDueDate(null);

        return todoRepository.save(todo);
    }

    public Todo updateComment(Long id, String comment) {
        Todo todo = findById(id);
        todo.setComment(normalizeComment(comment));

        return todoRepository.save(todo);
    }

    public Todo toggleCompleted(Long id) {
        Todo todo = findById(id);
        boolean completed = !todo.isCompleted();
        todo.setCompleted(completed);
        todo.setCompletedAt(completed ? LocalDateTime.now() : null);

        return todoRepository.save(todo);
    }

    public void delete(Long id) {
        Todo todo = findById(id);
        todoRepository.delete(todo);
    }

    private Todo findById(Long id) {
        return todoRepository.findById(id)
                .orElseThrow(() -> new TodoNotFoundException(id));
    }

    private String normalizeTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("???쇱쓣 ?낅젰?댁＜?몄슂.");
        }

        return title.trim();
    }

    private String normalizeComment(String comment) {
        if (comment == null || comment.trim().isEmpty()) {
            return null;
        }

        return comment.trim();
    }
}
