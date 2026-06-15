package com.youmh.yourloop;

import com.youmh.yourloop.entity.Todo;
import com.youmh.yourloop.entity.TodoGroup;
import com.youmh.yourloop.repository.TodoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
class YourLoopApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TodoRepository todoRepository;

    @BeforeEach
    void setUp() {
        todoRepository.deleteAll();
    }

    @Test
    void contextLoads() {
    }

    @Test
    void todoApiCanCreateAndFindTodos() throws Exception {
        mockMvc.perform(get("/api/todos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Vue connection\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Vue connection"))
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.groupType").value("UNCATEGORIZED"))
                .andExpect(jsonPath("$.dueDate").value(nullValue()))
                .andExpect(jsonPath("$.completedAt").value(nullValue()));

        mockMvc.perform(get("/api/todos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Vue connection"))
                .andExpect(jsonPath("$[0].groupType").value("UNCATEGORIZED"))
                .andExpect(jsonPath("$[0].dueDate").value(nullValue()));
    }

    @Test
    void todoApiRejectsBlankTitle() throws Exception {
        mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void todoApiFindsTodosNewestFirst() throws Exception {
        saveTodo("first todo", false);
        saveTodo("second todo", false);

        mockMvc.perform(get("/api/todos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title").value("second todo"))
                .andExpect(jsonPath("$[1].title").value("first todo"));
    }

    @Test
    void todoApiCanUpdateTodoTitle() throws Exception {
        Todo todo = saveTodo("old title", false);

        mockMvc.perform(put("/api/todos/{id}", todo.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"new title\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(todo.getId()))
                .andExpect(jsonPath("$.title").value("new title"))
                .andExpect(jsonPath("$.completed").value(false));
    }

    @Test
    void todoApiRejectsBlankTitleWhenUpdating() throws Exception {
        Todo todo = saveTodo("old title", false);

        mockMvc.perform(put("/api/todos/{id}", todo.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void todoApiReturnsNotFoundWhenUpdateTargetDoesNotExist() throws Exception {
        mockMvc.perform(put("/api/todos/{id}", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"new title\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Todo瑜?李얠쓣 ???놁뒿?덈떎. id=999"));
    }

    @Test
    void todoApiCanUpdateTodoGroup() throws Exception {
        Todo todo = saveTodo("group target", false);

        mockMvc.perform(put("/api/todos/{id}/group", todo.getId())
                        .param("groupType", "TODAY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(todo.getId()))
                .andExpect(jsonPath("$.groupType").value("TODAY"));
    }

    @Test
    void todoApiCanUpdateAndClearTodoDueDate() throws Exception {
        Todo todo = saveTodo("date target", false);

        mockMvc.perform(put("/api/todos/{id}/due-date", todo.getId())
                        .param("dueDate", "2026-06-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(todo.getId()))
                .andExpect(jsonPath("$.dueDate").value("2026-06-12"));

        mockMvc.perform(delete("/api/todos/{id}/due-date", todo.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(todo.getId()))
                .andExpect(jsonPath("$.dueDate").value(nullValue()));
    }

    @Test
    void todoApiCanUpdateTodoComment() throws Exception {
        Todo todo = saveTodo("comment target", true);

        mockMvc.perform(put("/api/todos/{id}/comment", todo.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"done after checking logs\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(todo.getId()))
                .andExpect(jsonPath("$.comment").value("done after checking logs"));

        mockMvc.perform(put("/api/todos/{id}/comment", todo.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comment").value(nullValue()));
    }

    @Test
    void todoApiCanToggleTodo() throws Exception {
        Todo todo = saveTodo("todo", false);

        mockMvc.perform(put("/api/todos/{id}/toggle", todo.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());

        mockMvc.perform(put("/api/todos/{id}/toggle", todo.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.completedAt").value(nullValue()));
    }

    @Test
    void todoApiReturnsNotFoundWhenToggleTargetDoesNotExist() throws Exception {
        mockMvc.perform(put("/api/todos/{id}/toggle", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Todo瑜?李얠쓣 ???놁뒿?덈떎. id=999"));
    }

    @Test
    void todoApiCanDeleteTodo() throws Exception {
        Todo todo = saveTodo("delete me", false);

        mockMvc.perform(delete("/api/todos/{id}", todo.getId()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/todos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void todoApiReturnsNotFoundWhenDeleteTargetDoesNotExist() throws Exception {
        mockMvc.perform(delete("/api/todos/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Todo瑜?李얠쓣 ???놁뒿?덈떎. id=999"));
    }

    private Todo saveTodo(String title, boolean completed) {
        Todo todo = new Todo();
        todo.setTitle(title);
        todo.setCompleted(completed);
        todo.setGroupType(TodoGroup.UNCATEGORIZED);

        return todoRepository.save(todo);
    }
}
