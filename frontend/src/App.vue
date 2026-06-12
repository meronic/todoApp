<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/todos'
const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

const GROUPS = [
  { value: 'TODAY', label: '오늘 바로 할 일', shortLabel: '오늘', theme: 'red' },
  { value: 'NEXT', label: '그 다음 할 일', shortLabel: '다음', theme: 'blue' },
  { value: 'LATER', label: '급하지 않은 일', shortLabel: '나중', theme: 'yellow' },
  { value: 'UNCATEGORIZED', label: '미분류', shortLabel: '미분류', theme: 'green' }
]

const todos = ref([])
const title = ref('')
const loading = ref(false)
const errorMessage = ref('')
const editingTodoId = ref(null)
const editingTitle = ref('')
const editInput = ref(null)
const viewMode = ref('all')
const groupFilter = ref('ALL')
const draggedTodoId = ref(null)
const dragOverGroup = ref(null)
const dragOverDate = ref(null)
const isDragging = ref(false)
const currentMonth = ref(startOfMonth(new Date()))

const visibleTodos = computed(() => {
  if (groupFilter.value === 'ALL') {
    return todos.value
  }

  return todos.value.filter((todo) => todo.groupType === groupFilter.value)
})

const openTodoCount = computed(() => todos.value.filter((todo) => !todo.completed).length)
const completedTodoCount = computed(() => todos.value.filter((todo) => todo.completed).length)
const calendarTitle = computed(() => {
  const year = currentMonth.value.getFullYear()
  const month = currentMonth.value.getMonth() + 1

  return `${year}년 ${month}월`
})
const calendarDays = computed(() => {
  const monthStart = startOfMonth(currentMonth.value)
  const startDate = new Date(monthStart)
  startDate.setDate(monthStart.getDate() - monthStart.getDay())

  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(startDate)
    date.setDate(startDate.getDate() + index)
    const value = formatDateValue(date)
    const dateTodos = todosByDate(value)

    return {
      value,
      dayNumber: date.getDate(),
      inCurrentMonth: date.getMonth() === monthStart.getMonth(),
      isToday: isSameDateValue(value, new Date()),
      count: dateTodos.length,
      todos: dateTodos.slice(0, 3)
    }
  })
})

async function fetchTodos() {
  loading.value = true
  errorMessage.value = ''

  try {
    const response = await fetch(API_BASE_URL)

    if (!response.ok) {
      throw new Error('할 일 목록을 불러오지 못했습니다.')
    }

    todos.value = await response.json()
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

async function createTodo() {
  const trimmedTitle = title.value.trim()

  if (!trimmedTitle) {
    return
  }

  const response = await fetch(API_BASE_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      title: trimmedTitle
    })
  })

  if (response.ok) {
    const createdTodo = await response.json()
    todos.value = [createdTodo, ...todos.value]
    title.value = ''
  }
}

async function startEditing(todo) {
  editingTodoId.value = todo.id
  editingTitle.value = todo.title
  await nextTick()
  focusEditInput()
}

function handleTodoClick(todo) {
  if (!isDragging.value && editingTodoId.value !== todo.id) {
    startEditing(todo)
  }
}

function cancelEditing() {
  editingTodoId.value = null
  editingTitle.value = ''
}

function setEditInput(element) {
  editInput.value = element

  if (element) {
    requestAnimationFrame(() => {
      element.focus()
    })
  }
}

function focusEditInput() {
  const element = Array.isArray(editInput.value) ? editInput.value[0] : editInput.value
  element?.focus()
}

function replaceTodo(updatedTodo) {
  todos.value = todos.value.map((todo) => {
    if (todo.id === updatedTodo.id) {
      return updatedTodo
    }

    return todo
  })
}

function todosByGroup(groupType) {
  return todos.value.filter((todo) => todo.groupType === groupType)
}

function todosByDate(dueDate) {
  return todos.value.filter((todo) => todo.dueDate === dueDate)
}

function countByGroup(groupType) {
  return todosByGroup(groupType).length
}

function groupByType(groupType) {
  return GROUPS.find((group) => group.value === groupType) || GROUPS[3]
}

function groupLabel(groupType) {
  return groupByType(groupType).label
}

function groupTheme(groupType) {
  return groupByType(groupType).theme
}

function startOfMonth(date) {
  return new Date(date.getFullYear(), date.getMonth(), 1)
}

function moveMonth(monthOffset) {
  currentMonth.value = new Date(
    currentMonth.value.getFullYear(),
    currentMonth.value.getMonth() + monthOffset,
    1
  )
}

function formatDateValue(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')

  return `${year}-${month}-${day}`
}

function isSameDateValue(value, date) {
  return value === formatDateValue(date)
}

function displayDueDate(dueDate) {
  if (!dueDate) {
    return '날짜 없음'
  }

  if (isSameDateValue(dueDate, new Date())) {
    return '오늘'
  }

  return dueDate.slice(5).replace('-', '/')
}

function showAll(filter = 'ALL') {
  viewMode.value = 'all'
  groupFilter.value = filter
}

function showGroupBoard() {
  viewMode.value = 'group'
  groupFilter.value = 'ALL'
}

function startDragging(todo, event) {
  draggedTodoId.value = todo.id
  isDragging.value = true
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData('text/plain', String(todo.id))
}

function finishDragging() {
  draggedTodoId.value = null
  dragOverGroup.value = null
  dragOverDate.value = null

  setTimeout(() => {
    isDragging.value = false
  }, 0)
}

async function dropTodoToGroup(groupType) {
  const todo = todos.value.find((item) => item.id === draggedTodoId.value)

  if (!todo || todo.groupType === groupType) {
    finishDragging()
    return
  }

  await updateTodoGroup(todo, groupType)
  finishDragging()
}

async function dropTodoToDate(dueDate) {
  const todo = todos.value.find((item) => item.id === draggedTodoId.value)

  if (!todo || todo.dueDate === dueDate) {
    finishDragging()
    return
  }

  await updateTodoDueDate(todo, dueDate)
  finishDragging()
}

async function updateTodo(todo) {
  const trimmedTitle = editingTitle.value.trim()

  if (!trimmedTitle) {
    return
  }

  const response = await fetch(`${API_BASE_URL}/${todo.id}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      title: trimmedTitle
    })
  })

  if (response.ok) {
    const updatedTodo = await response.json()
    replaceTodo(updatedTodo)
    cancelEditing()
  }
}

async function updateTodoGroup(todo, groupType) {
  const response = await fetch(`${API_BASE_URL}/${todo.id}/group?groupType=${groupType}`, {
    method: 'PUT'
  })

  if (response.ok) {
    const updatedTodo = await response.json()
    replaceTodo(updatedTodo)
  }
}

async function updateTodoDueDate(todo, dueDate) {
  const response = await fetch(`${API_BASE_URL}/${todo.id}/due-date?dueDate=${dueDate}`, {
    method: 'PUT'
  })

  if (response.ok) {
    const updatedTodo = await response.json()
    replaceTodo(updatedTodo)
  }
}

async function clearTodoDueDate(todo) {
  const response = await fetch(`${API_BASE_URL}/${todo.id}/due-date`, {
    method: 'DELETE'
  })

  if (response.ok) {
    const updatedTodo = await response.json()
    replaceTodo(updatedTodo)
  }
}

async function toggleTodo(todo) {
  const response = await fetch(`${API_BASE_URL}/${todo.id}/toggle`, {
    method: 'PUT'
  })

  if (response.ok) {
    const updatedTodo = await response.json()
    replaceTodo(updatedTodo)
  }
}

async function deleteTodo(todo) {
  const response = await fetch(`${API_BASE_URL}/${todo.id}`, {
    method: 'DELETE'
  })

  if (response.ok) {
    todos.value = todos.value.filter((item) => item.id !== todo.id)
  }
}

onMounted(() => {
  fetchTodos()
})
</script>

<template>
  <main class="app-shell" @click="editingTodoId !== null && cancelEditing()">
    <aside class="sidebar" @click.stop>
      <div class="brand">
        <p>Vue3 + Spring Boot</p>
        <h1>TaskFlow</h1>
      </div>

      <nav class="side-nav">
        <button type="button" :class="{ active: viewMode === 'all' }" @click="showAll()">
          <span>전체</span>
          <strong>{{ todos.length }}</strong>
        </button>
        <button type="button" :class="{ active: viewMode === 'group' }" @click="showGroupBoard">
          <span>그룹 보드</span>
          <strong>4</strong>
        </button>
      </nav>

      <section class="side-section">
        <h2>그룹 필터</h2>
        <button
          type="button"
          class="group-filter"
          :class="{ active: viewMode === 'all' && groupFilter === 'ALL' }"
          @click="showAll('ALL')"
        >
          <span>전체 그룹</span>
          <strong>{{ todos.length }}</strong>
        </button>
        <button
          v-for="group in GROUPS"
          :key="group.value"
          type="button"
          class="group-filter"
          :class="[`theme-${group.theme}`, { active: viewMode === 'all' && groupFilter === group.value }]"
          @click="showAll(group.value)"
        >
          <span>{{ group.shortLabel }}</span>
          <strong>{{ countByGroup(group.value) }}</strong>
        </button>
      </section>

      <section class="summary">
        <div>
          <span>진행 중</span>
          <strong>{{ openTodoCount }}</strong>
        </div>
        <div>
          <span>완료</span>
          <strong>{{ completedTodoCount }}</strong>
        </div>
      </section>
    </aside>

    <section class="workspace">
      <header class="workspace-header">
        <div>
          <p class="eyebrow">{{ viewMode === 'all' ? 'List View' : 'Board View' }}</p>
          <h2>{{ viewMode === 'all' ? '전체 할 일' : '그룹별 할 일' }}</h2>
        </div>
        <div v-if="viewMode === 'all'" class="active-filter">
          {{ groupFilter === 'ALL' ? '전체 그룹' : groupLabel(groupFilter) }}
        </div>
      </header>

      <form class="add-form" @submit.prevent="createTodo">
        <input v-model="title" type="text" placeholder="할 일을 입력하세요" />
        <button type="submit">추가</button>
      </form>

      <p v-if="errorMessage" class="message error">{{ errorMessage }}</p>
      <p v-else-if="loading" class="message">불러오는 중...</p>
      <p v-else-if="viewMode === 'group' && todos.length === 0" class="message">
        아직 등록한 할 일이 없습니다.
      </p>

      <div v-else-if="viewMode === 'all'" class="all-layout">
        <ul class="todo-list all-list">
          <li v-if="visibleTodos.length === 0" class="empty-list">
            아직 등록한 할 일이 없습니다.
          </li>
          <li
            v-for="todo in visibleTodos"
            :key="todo.id"
            class="todo-item draggable"
            :class="[
              `theme-${groupTheme(todo.groupType)}`,
              {
                editable: editingTodoId !== todo.id,
                completedItem: todo.completed,
                dragging: draggedTodoId === todo.id
              }
            ]"
            :draggable="editingTodoId !== todo.id"
            @dragstart.stop="startDragging(todo, $event)"
            @dragend="finishDragging"
            @click.stop="handleTodoClick(todo)"
          >
            <template v-if="editingTodoId === todo.id">
              <form class="edit-form" @submit.prevent="updateTodo(todo)" @click.stop>
                <input :ref="setEditInput" v-model="editingTitle" type="text" />
                <button type="submit">저장</button>
                <button type="button" class="secondary-button" @click="cancelEditing">취소</button>
              </form>
            </template>

            <template v-else>
              <div class="todo-content">
                <button
                  type="button"
                  class="check-hit-area"
                  :aria-label="todo.completed ? '완료됨' : '완료로 변경'"
                  @click.stop="toggleTodo(todo)"
                >
                  <span class="check-button" :class="{ checked: todo.completed }">
                    <span class="check-mark">✓</span>
                  </span>
                </button>
                <span class="todo-title" :class="{ completed: todo.completed }">{{ todo.title }}</span>
              </div>
              <div class="todo-actions">
                <span v-if="todo.dueDate" class="date-badge">
                  {{ displayDueDate(todo.dueDate) }}
                </span>
                <span class="group-badge" :class="`theme-${groupTheme(todo.groupType)}`">
                  {{ groupLabel(todo.groupType) }}
                </span>
                <button type="button" class="delete-button icon-button" aria-label="삭제" title="삭제" @click.stop="deleteTodo(todo)">
                  <span aria-hidden="true">×</span>
                </button>
              </div>
            </template>
          </li>
        </ul>

        <aside class="calendar-panel">
          <header class="calendar-header">
            <div>
              <p class="eyebrow">Schedule</p>
              <h3>{{ calendarTitle }}</h3>
            </div>
            <div class="calendar-nav">
              <button type="button" aria-label="이전 달" @click="moveMonth(-1)">‹</button>
              <button type="button" aria-label="다음 달" @click="moveMonth(1)">›</button>
            </div>
          </header>

          <div class="calendar-weekdays">
            <span v-for="weekday in WEEKDAYS" :key="weekday">{{ weekday }}</span>
          </div>

          <div class="calendar-grid month-grid">
            <button
              v-for="day in calendarDays"
              :key="day.value"
              type="button"
              class="calendar-day"
              :class="{
                outside: !day.inCurrentMonth,
                today: day.isToday,
                dragOver: dragOverDate === day.value
              }"
              @dragover.prevent="dragOverDate = day.value"
              @dragleave="dragOverDate === day.value && (dragOverDate = null)"
              @drop.prevent="dropTodoToDate(day.value)"
            >
              <div class="calendar-day-header">
                <span>{{ day.dayNumber }}</span>
                <strong v-if="day.count > 0">{{ day.count }}</strong>
              </div>
              <div v-if="day.todos.length > 0" class="calendar-todos">
                <span
                  v-for="todo in day.todos"
                  :key="todo.id"
                  class="calendar-todo-title"
                  :class="[
                    `theme-${groupTheme(todo.groupType)}`,
                    { completed: todo.completed, dragging: draggedTodoId === todo.id }
                  ]"
                  :title="todo.title"
                  draggable="true"
                  @dragstart.stop="startDragging(todo, $event)"
                  @dragend.stop="finishDragging"
                  @click.stop
                >
                  {{ todo.title }}
                </span>
                <span v-if="day.count > day.todos.length" class="calendar-more">
                  +{{ day.count - day.todos.length }}
                </span>
              </div>
            </button>
          </div>
        </aside>
      </div>

      <div v-else class="group-board">
        <section
          v-for="group in GROUPS"
          :key="group.value"
          class="group-section"
          :class="[`theme-${group.theme}`, { dragOver: dragOverGroup === group.value }]"
          @dragover.prevent="dragOverGroup = group.value"
          @dragleave="dragOverGroup === group.value && (dragOverGroup = null)"
          @drop.prevent="dropTodoToGroup(group.value)"
        >
          <header class="group-header">
            <h2>{{ group.label }}</h2>
            <span>{{ todosByGroup(group.value).length }}</span>
          </header>

          <p v-if="todosByGroup(group.value).length === 0" class="group-empty">
            여기에 놓아 분류해보세요.
          </p>

          <ul v-else class="todo-list">
            <li
              v-for="todo in todosByGroup(group.value)"
              :key="todo.id"
              class="todo-item draggable"
              :class="{
                editable: editingTodoId !== todo.id,
                completedItem: todo.completed,
                dragging: draggedTodoId === todo.id
              }"
              :draggable="editingTodoId !== todo.id"
              @dragstart.stop="startDragging(todo, $event)"
              @dragend="finishDragging"
              @click.stop="handleTodoClick(todo)"
            >
              <template v-if="editingTodoId === todo.id">
                <form class="edit-form" @submit.prevent="updateTodo(todo)" @click.stop>
                  <input :ref="setEditInput" v-model="editingTitle" type="text" />
                  <button type="submit">저장</button>
                  <button type="button" class="secondary-button" @click="cancelEditing">취소</button>
                </form>
              </template>

              <template v-else>
                <div class="todo-content">
                  <button
                    type="button"
                    class="check-hit-area"
                    :aria-label="todo.completed ? '완료됨' : '완료로 변경'"
                    @click.stop="toggleTodo(todo)"
                  >
                    <span class="check-button" :class="{ checked: todo.completed }">
                      <span class="check-mark">✓</span>
                    </span>
                  </button>
                  <span class="todo-title" :class="{ completed: todo.completed }">{{ todo.title }}</span>
                </div>
                <div class="todo-actions">
                  <span v-if="todo.dueDate" class="date-badge">
                    {{ displayDueDate(todo.dueDate) }}
                  </span>
                  <button type="button" class="delete-button icon-button" aria-label="삭제" title="삭제" @click.stop="deleteTodo(todo)">
                    <span aria-hidden="true">×</span>
                  </button>
                </div>
              </template>
            </li>
          </ul>
        </section>
      </div>
    </section>
  </main>
</template>
