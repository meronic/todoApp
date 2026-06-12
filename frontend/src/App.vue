<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/todos'
const THEME_STORAGE_KEY = 'taskflow-theme'
const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']
const savedTheme = typeof window !== 'undefined' ? window.localStorage.getItem(THEME_STORAGE_KEY) : null

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
const calendarViewMode = ref('month')
const collapsedCompletedGroups = ref({})
const quickAddDate = ref(null)
const quickAddTitle = ref('')
const quickAddInput = ref(null)
const quickAddSaving = ref(false)
const recentlyCompletedTodoId = ref(null)
const selectedYearDate = ref(null)
const completedHistoryGroupFilter = ref('ALL')
const themeMode = ref(savedTheme === 'light' ? 'light' : 'dark')
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
const scheduledTodoCount = computed(() => todos.value.filter((todo) => todo.dueDate).length)
const completedHistoryFilterOptions = computed(() => {
  const completedTodos = todos.value.filter((todo) => todo.completed && todo.completedAt)

  return [
    {
      value: 'ALL',
      label: '전체 완료',
      shortLabel: '전체',
      theme: 'all',
      icon: '✓',
      count: completedTodos.length
    },
    ...GROUPS.map((group) => ({
      ...group,
      icon: group.shortLabel.slice(0, 1),
      count: completedTodos.filter((todo) => todo.groupType === group.value).length
    }))
  ]
})
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

  if (viewMode.value === 'calendar') {
    return '캘린더'
  }

  if (viewMode.value === 'completed') {
    return '완료 이력'
  }

  return groupFilter.value === 'ALL' ? '전체 할 일' : groupLabel(groupFilter.value)
})
const completedHistoryGroups = computed(() => {
  const completedTodos = searchedTodos.value
    .filter((todo) => todo.completed && todo.completedAt)
    .filter((todo) => completedHistoryGroupFilter.value === 'ALL' || todo.groupType === completedHistoryGroupFilter.value)
    .slice()
    .sort((a, b) => b.completedAt.localeCompare(a.completedAt))

  return completedTodos.reduce((groups, todo) => {
    const date = todo.completedAt.slice(0, 10)
    const group = groups.find((item) => item.date === date)

    if (group) {
      group.todos.push(todo)
      return groups
    }

    groups.push({
      date,
      label: formatCompletedDateLabel(date),
      todos: [todo]
    })

    return groups
  }, [])
})
const calendarTitle = computed(() => {
  const year = currentMonth.value.getFullYear()
  const month = currentMonth.value.getMonth() + 1

  return calendarViewMode.value === 'year' ? `${year}년` : `${year}년 ${month}월`
})
const yearCalendarMonths = computed(() => {
  const year = currentMonth.value.getFullYear()

  return Array.from({ length: 12 }, (_, monthIndex) => ({
    value: monthIndex,
    label: `${monthIndex + 1}월`,
    days: buildCalendarDays(new Date(year, monthIndex, 1), 0)
  }))
})
const calendarDays = computed(() => {
  return buildCalendarDays(currentMonth.value, viewMode.value === 'calendar' ? 6 : 3)
})
const selectedYearTodos = computed(() => {
  if (!selectedYearDate.value) {
    return []
  }

  return todosByDate(selectedYearDate.value)
})

function buildCalendarDays(baseDate, todoLimit) {
  const monthStart = startOfMonth(baseDate)
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
      todos: dateTodos.slice(0, todoLimit)
    }
  })
}

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

function moveCalendar(offset) {
  selectedYearDate.value = null

  if (calendarViewMode.value === 'year') {
    currentMonth.value = new Date(currentMonth.value.getFullYear() + offset, 0, 1)
    return
  }

  moveMonth(offset)
}

function setCalendarViewMode(mode) {
  calendarViewMode.value = mode
  selectedYearDate.value = null
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

function formatCompletedDateLabel(dateValue) {
  const date = new Date(`${dateValue}T00:00:00`)
  const weekdays = ['일', '월', '화', '수', '목', '금', '토']

  return `${date.getFullYear()}년 ${date.getMonth() + 1}월 ${date.getDate()}일 ${weekdays[date.getDay()]}`
}

function formatCompletedTime(completedAt) {
  if (!completedAt) {
    return '-'
  }

  const date = new Date(completedAt)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')

  return `${year}/${month}/${day} ${hour}:${minute}`
}

function completionStatus(todo) {
  if (!todo.dueDate) {
    return { label: '일정 없음', tone: 'none' }
  }

  if (!todo.completedAt) {
    return { label: '완료 시각 없음', tone: 'none' }
  }

  const completedDate = todo.completedAt.slice(0, 10)

  if (completedDate < todo.dueDate) {
    return { label: '미리 완료', tone: 'early' }
  }

  if (completedDate > todo.dueDate) {
    return { label: '기한 초과 완료', tone: 'late' }
  }

  return { label: '기한 내 완료', tone: 'on-time' }
}

function yearDayDensityClass(day) {
  if (day.count >= 4) {
    return 'density-high'
  }

  if (day.count >= 2) {
    return 'density-medium'
  }

  if (day.count === 1) {
    return 'density-low'
  }

  return 'density-empty'
}

function selectYearDate(day) {
  selectedYearDate.value = day.count > 0 ? day.value : null
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
    let updatedTodo = await dueDateResponse.json()

    if (viewMode.value === 'all' && groupFilter.value !== 'ALL') {
      const groupResponse = await fetch(`${API_BASE_URL}/${updatedTodo.id}/group?groupType=${groupFilter.value}`, {
        method: 'PUT'
      })

      if (groupResponse.ok) {
        updatedTodo = await groupResponse.json()
      }
    }

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

function showCalendar() {
  viewMode.value = 'calendar'
  groupFilter.value = 'ALL'
}

function showCompleted() {
  viewMode.value = 'completed'
  groupFilter.value = 'ALL'
}

function setTheme(mode) {
  themeMode.value = mode

  if (typeof window !== 'undefined') {
    window.localStorage.setItem(THEME_STORAGE_KEY, mode)
    document.body.dataset.theme = mode
  }
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
      }, 1350)
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

    if (updatedTodo.completed) {
      recentlyCompletedTodoId.value = updatedTodo.id

      setTimeout(() => {
        if (recentlyCompletedTodoId.value === updatedTodo.id) {
          recentlyCompletedTodoId.value = null
        }
      }, 1350)
    }
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

async function updateTodoComment(todo) {
  const response = await fetch(`${API_BASE_URL}/${todo.id}/comment`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      comment: todo.comment || ''
    })
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

    if (updatedTodo.completed) {
      recentlyCompletedTodoId.value = updatedTodo.id

      setTimeout(() => {
        if (recentlyCompletedTodoId.value === updatedTodo.id) {
          recentlyCompletedTodoId.value = null
        }
      }, 1350)
    }
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
  setTheme(themeMode.value)
  fetchTodos()
})
</script>

<template>
  <main
    class="app-shell"
    :class="themeMode === 'light' ? 'theme-light' : 'theme-dark'"
    @click="editingTodoId !== null && cancelEditing(); quickAddDate !== null && finishQuickAdd(); selectedYearDate !== null && (selectedYearDate = null)"
  >
    <aside class="sidebar" @click.stop>
      <button type="button" class="brand" @click="showAll()">

        <h1>TaskFlow</h1>
      </button>

      <nav class="side-nav">
        <button type="button" :class="{ active: viewMode === 'all' }" @click="showAll()">
          <span>전체</span>
          <strong>{{ todos.length }}</strong>
        </button>
        <button type="button" :class="{ active: viewMode === 'group' }" @click="showGroupBoard">
          <span>아이젠하워 매트릭스</span>
          <strong>4</strong>
        </button>
        <button type="button" :class="{ active: viewMode === 'calendar' }" @click="showCalendar">
          <span>캘린더</span>
          <strong>{{ scheduledTodoCount }}</strong>
        </button>
        <button type="button" :class="{ active: viewMode === 'completed' }" @click="showCompleted">
          <span>완료</span>
          <strong>{{ completedTodoCount }}</strong>
        </button>
      </nav>

      <section class="theme-switcher" aria-label="테마 선택">
        <button type="button" :class="{ active: themeMode === 'dark' }" @click="setTheme('dark')">
          Dark
        </button>
        <button type="button" :class="{ active: themeMode === 'light' }" @click="setTheme('light')">
          Light
        </button>
      </section>

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

      <form v-if="viewMode !== 'calendar' && viewMode !== 'completed'" class="add-form" @submit.prevent="createTodo">
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
                    {
                      completed: todo.completed,
                      completedFlash: recentlyCompletedTodoId === todo.id,
                      dragging: draggedTodoId === todo.id
                    }
                  ]"
                  :title="todo.title"
                  draggable="true"
                  @dragstart.stop="startDragging(todo, $event)"
                  @dragend.stop="finishDragging"
                  @click.stop="toggleTodo(todo)"
                >
                  <span class="calendar-check" :class="{ checked: todo.completed }">
                    <span aria-hidden="true">✓</span>
                  </span>
                  <span class="calendar-title-text">{{ todo.title }}</span>
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

      <div v-else-if="viewMode === 'calendar'" class="calendar-page">
        <section class="calendar-panel calendar-page-panel">
          <header class="calendar-header">
            <div>
              <p class="eyebrow">Schedule</p>
              <h3>{{ calendarTitle }}</h3>
            </div>
            <div class="calendar-header-actions">
              <div class="calendar-view-toggle">
                <button type="button" :class="{ active: calendarViewMode === 'month' }" @click="setCalendarViewMode('month')">
                  월간
                </button>
                <button type="button" :class="{ active: calendarViewMode === 'year' }" @click="setCalendarViewMode('year')">
                  연간
                </button>
              </div>
              <div class="calendar-nav">
                <button type="button" :aria-label="calendarViewMode === 'year' ? '이전 해' : '이전 달'" @click="moveCalendar(-1)">‹</button>
                <button type="button" :aria-label="calendarViewMode === 'year' ? '다음 해' : '다음 달'" @click="moveCalendar(1)">›</button>
              </div>
            </div>
          </header>

          <template v-if="calendarViewMode === 'month'">
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
                      {
                        completed: todo.completed,
                        completedFlash: recentlyCompletedTodoId === todo.id,
                        dragging: draggedTodoId === todo.id
                      }
                    ]"
                    :title="todo.title"
                    draggable="true"
                    @dragstart.stop="startDragging(todo, $event)"
                    @dragend.stop="finishDragging"
                    @click.stop="toggleTodo(todo)"
                  >
                    <span class="calendar-check" :class="{ checked: todo.completed }">
                      <span aria-hidden="true">✓</span>
                    </span>
                    <span class="calendar-title-text">{{ todo.title }}</span>
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
          </template>

          <template v-else>
            <div class="year-calendar-grid">
              <section v-for="month in yearCalendarMonths" :key="month.value" class="year-month">
                <h4>{{ month.label }}</h4>
                <div class="year-weekdays">
                  <span v-for="weekday in WEEKDAYS" :key="weekday">{{ weekday }}</span>
                </div>
                <div class="year-days">
                  <div
                    v-for="day in month.days"
                    :key="day.value"
                    class="year-day"
                    role="button"
                    tabindex="0"
                    :class="[
                      yearDayDensityClass(day),
                      {
                        outside: !day.inCurrentMonth,
                        today: day.isToday,
                        selected: selectedYearDate === day.value,
                        dragOver: dragOverDate === day.value
                      }
                    ]"
                    :title="day.count > 0 ? `${day.value} 할 일 ${day.count}개` : day.value"
                    @click.stop="selectYearDate(day)"
                    @keydown.space.prevent="selectYearDate(day)"
                    @dragover.prevent="dragOverDate = day.value"
                    @dragleave="dragOverDate === day.value && (dragOverDate = null)"
                    @drop.prevent="dropTodoToDate(day.value)"
                  >
                    <span>{{ day.dayNumber }}</span>
                    <strong v-if="day.count > 0">{{ day.count }}</strong>
                    <section v-if="selectedYearDate === day.value" class="year-date-popover" @click.stop>
                      <header>
                        <div>
                          <span>선택 날짜</span>
                          <strong>{{ selectedYearDate }}</strong>
                        </div>
                        <button type="button" aria-label="닫기" @click.stop="selectedYearDate = null">×</button>
                      </header>
                      <div class="year-date-todos">
                        <button
                          v-for="todo in selectedYearTodos"
                          :key="todo.id"
                          type="button"
                          class="year-date-todo"
                          :class="[`theme-${groupTheme(todo.groupType)}`, { completed: todo.completed }]"
                          @click.stop="toggleTodo(todo)"
                        >
                          <span class="calendar-check" :class="{ checked: todo.completed }">
                            <span aria-hidden="true">✓</span>
                          </span>
                          <span>{{ todo.title }}</span>
                          <small>{{ groupLabel(todo.groupType) }}</small>
                        </button>
                      </div>
                    </section>
                  </div>
                </div>
              </section>
            </div>
          </template>
        </section>
      </div>

      <div v-else-if="viewMode === 'completed'" class="completed-history">
        <aside class="completed-history-filter">
          <section class="completed-filter-section">
            <h3>리스트</h3>
            <button
              v-for="option in completedHistoryFilterOptions"
              :key="option.value"
              type="button"
              class="completed-filter-item"
              :class="[
                option.value === 'ALL' ? 'theme-all' : `theme-${option.theme}`,
                { active: completedHistoryGroupFilter === option.value }
              ]"
              @click="completedHistoryGroupFilter = option.value"
            >
              <span class="completed-filter-icon">{{ option.icon }}</span>
              <span class="completed-filter-label">{{ option.label }}</span>
              <span class="completed-filter-dot" aria-hidden="true"></span>
              <strong>{{ option.count }}</strong>
            </button>
          </section>
        </aside>

        <div class="completed-history-content">
          <p v-if="completedHistoryGroups.length === 0" class="empty-list">
            {{ searchTerm ? '검색 결과가 없습니다.' : '아직 완료한 할 일이 없습니다.' }}
          </p>

          <template v-else>
            <section
              v-for="historyGroup in completedHistoryGroups"
              :key="historyGroup.date"
              class="completed-history-group"
            >
              <header class="completed-history-date">
                <span>{{ historyGroup.label }}</span>
                <strong>{{ historyGroup.todos.length }}</strong>
              </header>

              <ul class="completed-history-list">
                <li
                  v-for="todo in historyGroup.todos"
                  :key="todo.id"
                  class="completed-history-item"
                  :class="`theme-${groupTheme(todo.groupType)}`"
                >
                  <div class="completed-history-task">
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

                    <div class="completed-history-main">
                      <strong>{{ todo.title }}</strong>
                    </div>

                    <div class="completed-history-meta">
                      <span class="group-badge" :class="`theme-${groupTheme(todo.groupType)}`">
                        {{ groupLabel(todo.groupType) }}
                      </span>
                      <span v-if="todo.dueDate" class="date-badge">일정 {{ displayDueDate(todo.dueDate) }}</span>
                      <span v-else class="date-badge muted">일정 없음</span>
                      <span
                        class="completion-status"
                        :class="`tone-${completionStatus(todo).tone}`"
                      >
                        {{ completionStatus(todo).label }}
                      </span>
                    </div>

                    <div class="completed-history-time">
                      <span>완료 시간 :</span>
                      <strong>{{ formatCompletedTime(todo.completedAt) }}</strong>
                    </div>
                  </div>

                  <label class="completed-history-comment" @click.stop>
                    <span>코멘트</span>
                    <input
                      v-model="todo.comment"
                      type="text"
                      placeholder="완료 메모 입력"
                      @blur="updateTodoComment(todo)"
                      @keydown.enter.prevent="$event.target.blur()"
                    />
                  </label>
                </li>
              </ul>
            </section>
          </template>
        </div>
      </div>

      <div v-else-if="viewMode === 'group'" class="group-board" :class="{ dragging: isDragging }">
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
              <button
                type="button"
                class="group-subheader toggle-subheader"
                :class="{ collapsed: isCompletedSectionCollapsed(group.value) }"
                @click.stop="toggleCompletedSection(group.value)"
              >
                <span>{{ isCompletedSectionCollapsed(group.value) ? '완료' : '완료' }}</span>
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
