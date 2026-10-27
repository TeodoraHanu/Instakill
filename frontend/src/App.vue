<template>
  <div class="app-shell">
    <header class="app-header">
      <h1>Instakill</h1>
      <nav v-if="user">
        <span class="user">@{{ user.username }}</span>
        <button @click="logout">Logout</button>
      </nav>
    </header>

    <main class="content">
      <section v-if="!user" class="card">
        <div class="tabs">
          <button :class="{ active: authMode === 'login' }" @click="authMode = 'login'">Login</button>
          <button :class="{ active: authMode === 'register' }" @click="authMode = 'register'">Register</button>
        </div>
        <form @submit.prevent="submitAuth" class="form">
          <input v-model="authForm.usernameOrEmail" v-if="authMode === 'login'" placeholder="Username or email" required>
          <input v-model="authForm.username" v-else placeholder="Username" required minlength="3">
          <input v-model="authForm.email" v-if="authMode === 'register'" type="email" placeholder="Email" required>
          <input v-model="authForm.password" type="password" placeholder="Password" required minlength="6">
          <p v-if="error" class="error">{{ error }}</p>
          <button type="submit">Continue</button>
        </form>
      </section>

      <section v-else class="feed">
        <form @submit.prevent="createNewPost" class="card form composer">
          <textarea v-model="composer.content" rows="3" placeholder="Share something..."></textarea>
          
          <div class="media-upload-box">
            <input type="file" accept="image/*,video/*" @change="handleFileUpload" />
            <span v-if="uploadStatus" :style="{ color: uploadStatusColor }" class="upload-status">
              {{ uploadStatus }}
            </span>
          </div>

          <div v-if="composer.imageUrl" class="media-preview-container">
            <p style="font-size: 0.85rem; font-weight: bold; margin-bottom: 0.25rem;">Preview:</p>
            
            <img 
              v-if="!isVideo(composer.imageUrl)" 
              :src="formatMediaUrl(composer.imageUrl)" 
              class="preview-media"
            >

            <video 
              v-else 
              :key="formatMediaUrl(composer.imageUrl)"
              controls 
              preload="metadata"
              class="preview-media"
            >
              <source :src="formatMediaUrl(composer.imageUrl)" type="video/mp4">
              Browserul tău nu suportă redarea acestui video.
            </video>

            <button type="button" @click="clearMedia" class="remove-media-btn">Remove Media</button>
          </div>

          <input v-model="composer.imageUrl" placeholder="Image URL (optional)">
          <div class="actions">
            <button type="submit">Post</button>
          </div>
        </form>

        <div class="feed-toolbar">
          <h2>Feed</h2>
          <div class="sort">
            <button :class="{ active: sort === 'new' }" @click="changeSort('new')">Newest</button>
            <button :class="{ active: sort === 'trending' }" @click="changeSort('trending')">Trending</button>
          </div>
        </div>

        <article v-for="post in posts" :key="post.id" class="card post">
          <header>
            <div>
              <h3>@{{ post.author.username }}</h3>
              <small>{{ formatDate(post.createdAt) }}</small>
            </div>
            <div class="metrics">
              <span>{{ post.likeCount }} likes</span>
              <span> | </span>
              <span>{{ post.commentCount }} comments</span>
            </div>
          </header>
          <p v-if="post.content && post.content.trim() !== '.' && post.content.trim() !== ''">{{ post.content }}</p>

          <img v-if="post.imageUrl && !isVideo(post.imageUrl)" :src="formatMediaUrl(post.imageUrl)" alt="post image">

          <video 
            v-if="post.imageUrl && isVideo(post.imageUrl)" 
            :key="formatMediaUrl(post.imageUrl)"
            controls 
            preload="metadata"
            style="width: 100%; border-radius: 12px; margin-top: 0.75rem;"
          >
            <source :src="formatMediaUrl(post.imageUrl)" type="video/mp4">
            Browserul tău nu suportă redarea acestui video.
          </video>
          
          <!-- Comments Section -->
          <section class="comments">
            <div v-if="comments[post.id] && comments[post.id].length">
              <div v-for="comment in comments[post.id]" :key="comment.id" class="comment">
                <b>@{{ comment.author.username }}</b> <small>{{ formatDate(comment.createdAt) }}</small>
                <div>{{ comment.content }}</div>
              </div>
            </div>
            <div v-else class="no-comments">
              <em>No comments yet.</em>
            </div>
            <form v-if="user" @submit.prevent="submitComment(post.id)" class="comment-form">
              <input
                v-model="newComment[post.id]"
                :placeholder="'Add a comment...'"
                required
                minlength="1"
              />
              <button type="submit" :disabled="commentLoading[post.id]">Comment</button>
            </form>
            <div v-if="commentError[post.id]" class="error">{{ commentError[post.id] }}</div>
          </section>

          <footer>
            <button @click="togglePostLike(post)">
              {{ post.likedByCurrentUser ? 'Unlike' : 'Like' }}
            </button>
          </footer>
        </article>
      </section>
    </main>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { createPost, fetchMe, listFeed, login, register as registerUser, toggleLike, listComments, addComment } from './api'

const user = ref(null)
const posts = ref([])
const sort = ref('new')
const authMode = ref('login')
const error = ref('')
const authForm = reactive({ username: '', email: '', password: '', usernameOrEmail: '' })
const composer = reactive({ content: '', imageUrl: '' })

const uploadStatus = ref('')
const uploadStatusColor = ref('gray')

// Comments state
const comments = reactive({})
const newComment = reactive({})
const commentLoading = reactive({})
const commentError = reactive({})

onMounted(async () => {
  await bootstrap()
})

async function bootstrap() {
  try {
    user.value = await fetchMe()
    await loadFeed()
  } catch (e) {
    user.value = null
  }
}

async function handleFileUpload(event) {
  const file = event.target.files[0]
  if (!file) return

  uploadStatus.value = 'Uploading...'
  uploadStatusColor.value = '#f59e0b'

  const formData = new FormData()
  formData.append('file', file)

  try {
    const response = await fetch('http://localhost:8080/api/media', {
      method: 'POST',
      body: formData
    })

    if (response.status === 201 || response.ok) {
      const data = await response.json()
      composer.imageUrl = data.mediaUrl
      uploadStatus.value = 'Uploaded successfully!'
      uploadStatusColor.value = '#10b981'
    } else {
      uploadStatus.value = 'Upload failed!'
      uploadStatusColor.value = '#ef4444'
    }
  } catch (e) {
    uploadStatus.value = 'Server error!'
    uploadStatusColor.value = '#ef4444'
  }
}

function clearMedia() {
  composer.imageUrl = ''
  uploadStatus.value = ''
}

function formatMediaUrl(url) {
  if (!url) return ''
  if (url.startsWith('/media/')) {
    return 'http://localhost:8080' + url
  }
  return url
}

function isVideo(url) {
  if (!url) return false
  const lower = url.toLowerCase()
  return lower.endsWith('.mp4') || lower.endsWith('.mov') || lower.endsWith('.webm')
}

async function submitAuth() {
  error.value = ''
  try {
    if (authMode.value === 'login') {
      const result = await login({
        usernameOrEmail: authForm.usernameOrEmail,
        password: authForm.password
      })
      user.value = result.user
    } else {
      const result = await registerUser({
        username: authForm.username,
        email: authForm.email,
        password: authForm.password
      })
      user.value = result.user
    }
    await loadFeed()
  } catch (e) {
    error.value = e.response?.data?.detail || 'Authentication failed'
  }
}

async function loadFeed() {
  posts.value = await listFeed({ sort: sort.value })
  // Load comments for each post
  for (const post of posts.value) {
    await loadComments(post.id)
  }
}

async function loadComments(postId) {
  try {
    commentError[postId] = ''
    comments[postId] = await listComments(postId)
  } catch (e) {
    comments[postId] = []
    commentError[postId] = 'Failed to load comments'
  }
}

async function submitComment(postId) {
  if (!newComment[postId] || !newComment[postId].trim()) return
  commentLoading[postId] = true
  commentError[postId] = ''
  try {
    await addComment(postId, { content: newComment[postId] })
    newComment[postId] = ''
    await loadComments(postId)
    // Optionally, update commentCount in post
    const post = posts.value.find(p => p.id === postId)
    if (post) post.commentCount = (comments[postId]?.length || 0)
  } catch (e) {
    commentError[postId] = e.response?.data?.detail || 'Failed to add comment'
  } finally {
    commentLoading[postId] = false
  }
}

async function createNewPost() {
  const text = composer.content ? composer.content.trim() : ''
  const media = composer.imageUrl ? composer.imageUrl.trim() : ''
  
  if (!text && !media) return

  await createPost({ 
    content: text || '.', 
    imageUrl: media || null 
  })
  
  composer.content = ''
  composer.imageUrl = ''
  uploadStatus.value = ''
  await loadFeed()
}

async function togglePostLike(post) {
  const result = await toggleLike(post.id)
  post.likedByCurrentUser = result.liked
  post.likeCount = result.likeCount
}

function changeSort(next) {
  if (sort.value === next) return
  sort.value = next
  loadFeed()
}

function formatDate(iso) {
  return new Date(iso).toLocaleString()
}

async function logout() {
  document.cookie = 'ACCESS_TOKEN=; Max-Age=0; path=/'
  user.value = null
  posts.value = []
}
</script>

<style scoped>
.app-shell {
  max-width: 960px;
  margin: 0 auto;
  padding: 2rem 1rem;
}

.app-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
}

.app-header nav {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.app-header button {
  background: #111827;
  color: #fff;
  border: none;
  padding: 0.5rem 1rem;
  border-radius: 999px;
}

.content {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.card {
  background: #fff;
  border-radius: 16px;
  padding: 1.5rem;
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.1);
}

.form {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.form input,
.form textarea {
  width: 100%;
  padding: 0.75rem;
  border-radius: 10px;
  border: 1px solid #d1d5db;
}

.media-upload-box {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin: 0.25rem 0;
}

.upload-status {
  font-size: 0.85rem;
  font-weight: 600;
}

.media-preview-container {
  margin: 0.5rem 0;
  padding: 0.5rem;
  border: 1px dashed #d1d5db;
  border-radius: 10px;
  background-color: #f9fafb;
}

.preview-media {
  max-width: 100%;
  max-height: 250px;
  border-radius: 8px;
  display: block;
  margin-bottom: 0.5rem;
}

.remove-media-btn {
  background: #ef4444 !important;
  color: white;
  padding: 0.25rem 0.75rem !important;
  font-size: 0.8rem;
  border-radius: 6px !important;
}

.form button {
  align-self: flex-start;
  background: #2563eb;
  color: #fff;
  border: none;
  padding: 0.5rem 1.25rem;
  border-radius: 999px;
}

.tabs {
  display: flex;
  gap: 1rem;
  margin-bottom: 1rem;
}

.tabs button {
  background: transparent;
  border: none;
  font-weight: 600;
  color: #6b7280;
}

.tabs button.active {
  color: #111827;
}

.error {
  color: #ef4444;
}

.feed-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sort button {
  margin-left: 0.5rem;
  border: none;
  background: #e5e7eb;
  padding: 0.4rem 0.9rem;
  border-radius: 999px;
}

.sort button.active {
  background: #111827;
  color: #fff;
}

.post header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
}

.post img {
  margin-top: 0.75rem;
  width: 100%;
  border-radius: 12px;
}

.post footer {
  margin-top: 1rem;
}

.post footer button {
  background: #111827;
  border: none;
  color: #fff;
  padding: 0.4rem 1.2rem;
  border-radius: 999px;
}
.comments {
  margin-top: 1.2rem;
  padding-top: 1rem;
  border-top: 1px solid #e5e7eb;
}
.comment {
  margin-bottom: 0.7rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid #f3f4f6;
}
.no-comments {
  color: #6b7280;
  margin-bottom: 0.7rem;
}
.comment-form {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.5rem;
}
.comment-form input {
  flex: 1;
  padding: 0.5rem;
  border-radius: 8px;
  border: 1px solid #d1d5db;
}
.comment-form button {
  background: #2563eb;
  color: #fff;
  border: none;
  padding: 0.4rem 1.1rem;
  border-radius: 999px;
}
</style>