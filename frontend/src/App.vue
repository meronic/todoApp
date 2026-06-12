<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/todos'
const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

const GROUPS = [
  { value: 'TODAY', label: '즉시 처리', shortLabel: '즉시', theme: 'red' },
  { value: 'NEXT', label: '중요한 일', shortLabel: '중요', theme: 'blue' },
  { value: 'LATER', label: '계획 업무', shortLabel: '계획', theme: 'yellow' },
  { value: 'UNCATEGORIZED', label: '미분류', shortLabel: '미분류', theme: 'green' }
]
const DATE_BUCKETS = [
  { value: 'OVERDUE', label: '기한 초과' },
  { value: 'TODAY', label: '오늘' },
  { value: 'UPCOMING', label: '예정' },
  { value: 'NONE', label: '날짜 없음' }
]

const todos = ref([])
const title = ref('')
const searchQuery = ref('')
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
const collapsedCompletedGroups = ref({})
const quickAddDate = ref(null)
const quickAddTitle = ref('')
const quickAddInput = ref(null)
const quickAddSaving = ref(false)
const recentlyCompletedTodoId = ref(null)
let dragPreviewElement = null

const visibleTodos = computed(() => {
  if (groupFilter.value === 'ALL') {
    return searchedTodos.value
  }

  return searchedTodos.value.filter((todo) => todo.groupType === groupFilter.value)
})

const searchTerm = computed(() => searchQuery.value.trim().toLowerCase())
const searchedTodos = computed(() => {
  if (!searchTerm.value) {
    return todos.value
  }

  return todos.value.filter((todo) => todo.title.toLowerCase().includes(searchTerm.value))
})
const openTodoCount = computed(() => todos.value.filter((todo) => !todo.completed).length)
const completedTodoCount = computed(() => todos.value.filter((todo) => todo.completed).length)
const priorityStats = computed(() => {
  return GROUPS.map((group) => {
    const groupTodos = todos.value.filter((todo) => todo.groupType === group.value)

    return {
      ...group,
      total: groupTodos.length,
      open: groupTodos.filter((todo) => !todo.completed).length,
      completed: groupTodos.filter((todo) => todo.completed).length
    }
  })
})
const workspaceTitle = computed(() => {
  if (viewMode.value === 'group') {
    return '그룹별 할 일'
  }

  return groupFilter.value === 'ALL' ? '전체 할 일' : groupLabel(groupFilter.value)
})
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
    let createdTodo = await response.json()

    if (viewMode.value === 'all' && groupFilter.value !== 'ALL') {
      const groupResponse = await fetch(`${API_BASE_URL}/${createdTodo.id}/group?groupType=${groupFilter.value}`, {
        method: 'PUT'
      })

      if (groupResponse.ok) {
        createdTodo = await groupResponse.json()
      }
    }

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

function setQuickAddInput(element) {
  quickAddInput.value = element

  if (element) {
    requestAnimationFrame(() => {
      element.focus()
    })
  }
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
  return searchedTodos.value.filter((todo) => todo.groupType === groupType)
}

function activeTodosByGroup(groupType) {
  return todosByGroup(groupType).filter((todo) => !todo.completed)
}

function completedTodosByGroup(groupType) {
  return todosByGroup(groupType).filter((todo) => todo.completed)
}

function activeTodosByGroupAndDateBucket(groupType, bucket) {
  return activeTodosByGroup(groupType).filter((todo) => dateBucketOf(todo) === bucket)
}

function todosByDate(dueDate) {
  return searchedTodos.value.filter((todo) => todo.dueDate === dueDate)
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

function isCompletedSectionCollapsed(groupType) {
  return collapsedCompletedGroups.value[groupType] === true
}

function toggleCompletedSection(groupType) {
  collapsedCompletedGroups.value = {
    ...collapsedCompletedGroups.value,
    [groupType]: !isCompletedSectionCollapsed(groupType)
  }
}

function dateBucketOf(todo) {
  const today = formatDateValue(new Date())

  if (!todo.dueDate) {
    return 'NONE'
  }

  if (todo.dueDate < today) {
    return 'OVERDUE'
  }

  if (todo.dueDate === today) {
    return 'TODAY'
  }

  return 'UPCOMING'
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

async function openQuickAdd(dateValue) {
  if (isDragging.value) {
    return
  }

  quickAddDate.value = dateValue
  quickAddTitle.value = ''
  await nextTick()
  quickAddInput.value?.focus()
}

function cancelQuickAdd() {
  quickAddDate.value = null
  quickAddTitle.value = ''
}

async function finishQuickAdd() {
  if (quickAddTitle.value.trim()) {
    await createTodoForDate()
    return
  }

  cancelQuickAdd()
}

async function createTodoForDate() {
  const trimmedTitle = quickAddTitle.value.trim()

  if (!trimmedTitle || !quickAddDate.value || quickAddSaving.value) {
    return
  }

  quickAddSaving.value = true

  const response = await fetch(API_BASE_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      title: trimmedTitle
    })
  })

  if (!response.ok) {
    quickAddSaving.value = false
    return
  }

  const createdTodo = await response.json()
  const dueDate = quickAddDate.value
  const dueDateResponse = await fetch(`${API_BASE_URL}/${createdTodo.id}/due-date?dueDate=${dueDate}`, {
    method: 'PUT'
  })

  if (dueDateResponse.ok) {
    const updatedTodo = await dueDateResponse.json()
    todos.value = [updatedTodo, ...todos.value]
    cancelQuickAdd()
  }

  quickAddSaving.value = false
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
  setDragPreview(todo, event)
}

function finishDragging() {
  draggedTodoId.value = null
  dragOverGroup.value = null
  dragOverDate.value = null
  removeDragPreview()

  setTimeout(() => {
    isDragging.value = false
  }, 0)
}

function setDragPreview(todo, event) {
  removeDragPreview()

  const preview = document.createElement('div')
  preview.className = `drag-preview theme-${groupTheme(todo.groupType)}`
  preview.innerHTML = `
    <span>${escapeHtml(todo.title)}</span>
    <strong>${escapeHtml(groupLabel(todo.groupType))}</strong>
  `

  document.body.appendChild(preview)
  dragPreviewElement = preview
  event.dataTransfer.setDragImage(preview, 18, 18)
}

function removeDragPreview() {
  dragPreviewElement?.remove()
  dragPreviewElement = null
}

function escapeHtml(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;')
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

    if (updatedTodo.completed) {
      recentlyCompletedTodoId.value = updatedTodo.id

      setTimeout(() => {
        if (recentlyCompletedTodoId.value === updatedTodo.id) {
          recentlyCompletedTodoId.value = null
        }
      }, 900)
    }
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
  <main class="app-shell" @click="editingTodoId !== null && cancelEditing(); quickAddDate !== null && finishQuickAdd()">
    <aside class="sidebar" @click.stop>
      <button type="button" class="brand" @click="showAll()">
        <p>Vue3 + Spring Boot</p>
        <h1>TaskFlow</h1>
      </button>

      <section class="runtime-panel">
        <div>
          <span>API</span>
          <strong>8080</strong>
        </div>
        <div>
          <span>VITE</span>
          <strong>5174</strong>
        </div>
      </section>

      <nav class="side-nav">
        <button type="button" :class="{ active: viewMode === 'all' }" @click="showAll()">
          <span>전체</span>
          <strong>{{ todos.length }}</strong>
        </button>
        <button type="button" :class="{ active: viewMode === 'group' }" @click="showGroupBoard">
          <span>아이젠하워 매트릭스</span>
          <strong>4</strong>
        </button>
      </nav>

      <section class="side-section">
        <h2>그룹 필터</h2>
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

      <section class="side-section priority-section">
        <h2>우선순위 통계</h2>
        <div class="priority-stats">
          <article
            v-for="stat in priorityStats"
            :key="stat.value"
            class="priority-stat"
            :class="`theme-${stat.theme}`"
          >
            <span>{{ stat.shortLabel }}</span>
            <strong>{{ stat.open }}</strong>
            <small>완료 {{ stat.completed }} / 전체 {{ stat.total }}</small>
          </article>
        </div>
      </section>
    </aside>

    <section class="workspace">
      <header class="workspace-header">
        <div>
          <p class="eyebrow">{{ viewMode === 'all' ? 'List View' : 'Board View' }}</p>
          <h2>{{ workspaceTitle }}</h2>
        </div>
        <div class="header-tools">
          <label class="search-box">
            <span>SEARCH</span>
            <input v-model="searchQuery" type="search" placeholder="Search tasks..." />
          </label>
          <div v-if="viewMode === 'all'" class="active-filter">
            {{ groupFilter === 'ALL' ? '전체 그룹' : `${visibleTodos.length}개` }}
          </div>
        </div>
      </header>

      <form class="add-form" @submit.prevent="createTodo">
        <input v-model="title" type="text" placeholder="task add ..." />
        <button type="submit">ADD</button>
      </form>

      <p v-if="errorMessage" class="message error">{{ errorMessage }}</p>
      <p v-else-if="loading" class="message">불러오는 중...</p>
      <p v-else-if="viewMode === 'group' && todos.length === 0" class="message">
        아직 등록한 할 일이 없습니다.
      </p>

      <div v-else-if="viewMode === 'all'" class="all-layout">
        <ul class="todo-list all-list">
          <li v-if="visibleTodos.length === 0" class="empty-list">
            {{ searchTerm ? '검색 결과가 없습니다.' : '아직 등록한 할 일이 없습니다.' }}
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
                completedFlash: recentlyCompletedTodoId === todo.id,
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
            <div
              v-for="day in calendarDays"
              :key="day.value"
              class="calendar-day"
              role="button"
              tabindex="0"
              :class="{
                outside: !day.inCurrentMonth,
                today: day.isToday,
                dragOver: dragOverDate === day.value
              }"
              @click.stop="openQuickAdd(day.value)"
              @keydown.space.prevent="openQuickAdd(day.value)"
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
              <form
                v-if="quickAddDate === day.value"
                class="calendar-quick-add"
                @submit.prevent.stop="createTodoForDate"
                @click.stop
              >
                <input
                  :ref="setQuickAddInput"
                  v-model="quickAddTitle"
                  type="text"
                  placeholder="할 일 추가"
                  @blur="finishQuickAdd"
                  @keydown.esc.prevent="cancelQuickAdd"
                />
              </form>
            </div>
          </div>
        </aside>
      </div>

      <div v-else class="group-board" :class="{ dragging: isDragging }">
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

          <div v-else class="group-todo-area">
            <section class="group-todo-section">
              <header class="group-subheader">
                <span>진행 중</span>
                <strong>{{ activeTodosByGroup(group.value).length }}</strong>
              </header>

              <p v-if="activeTodosByGroup(group.value).length === 0" class="group-empty compact">
                진행 중인 할 일이 없습니다.
              </p>

              <div v-else class="date-bucket-list">
                <section
                  v-for="bucket in DATE_BUCKETS"
                  :key="bucket.value"
                  v-show="activeTodosByGroupAndDateBucket(group.value, bucket.value).length > 0"
                  class="date-bucket"
                  :class="`bucket-${bucket.value.toLowerCase()}`"
                >
                  <header class="date-bucket-header">
                    <span>{{ bucket.label }}</span>
                    <strong>{{ activeTodosByGroupAndDateBucket(group.value, bucket.value).length }}</strong>
                  </header>

                  <ul class="todo-list">
                    <li
                      v-for="todo in activeTodosByGroupAndDateBucket(group.value, bucket.value)"
                      :key="todo.id"
                      class="todo-item draggable"
                      :class="{
                        editable: editingTodoId !== todo.id,
                        completedItem: todo.completed,
                        completedFlash: recentlyCompletedTodoId === todo.id,
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

            <section v-if="completedTodosByGroup(group.value).length > 0" class="group-todo-section completed-section">
              <button type="button" class="group-subheader toggle-subheader" @click.stop="toggleCompletedSection(group.value)">
                <span>{{ isCompletedSectionCollapsed(group.value) ? '완료 보기' : '완료' }}</span>
                <strong>{{ completedTodosByGroup(group.value).length }}</strong>
              </button>

              <ul v-if="!isCompletedSectionCollapsed(group.value)" class="todo-list completed-list">
                <li
                  v-for="todo in completedTodosByGroup(group.value)"
                  :key="todo.id"
                  class="todo-item draggable completedItem"
                  :class="{
                    editable: editingTodoId !== todo.id,
                    completedFlash: recentlyCompletedTodoId === todo.id,
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
                        aria-label="완료 해제"
                        @click.stop="toggleTodo(todo)"
                      >
                        <span class="check-button checked">
                          <span class="check-mark">✓</span>
                        </span>
                      </button>
                      <span class="todo-title completed">{{ todo.title }}</span>
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
      </div>
    </section>
  </main>
</template>
