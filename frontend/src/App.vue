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

      <!-- Layout cu Feed-ul in stanga si Analytics pe margine (dreapta) -->
      <div v-else class="main-layout">
        <!-- COLOANA STÂNGA: Formular + Feed -->
        <div class="left-column">
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

          <section class="feed-list">
            <article v-for="post in posts" :key="post.id" class="card post">
              <header>
                <div>
                  <h3>@{{ post.author?.username || post.author }}</h3>
                  <small>{{ formatDate(post.createdAt) }}</small>
                </div>
                <div class="metrics">
                  <span>{{ post.likeCount }} likes</span>
                  <span> | </span>
                  <span>{{ post.commentCount }} comments</span>
                </div>
              </header>
              <p v-if="post.content && post.content.trim() !== '.' && post.content.trim() !== ''">{{ post.content }}</p>

              <img v-if="(post.imageUrl || post.mediaUrl) && !isVideo(post.imageUrl || post.mediaUrl)" :src="formatMediaUrl(post.imageUrl || post.mediaUrl)" alt="post image">

              <video 
                v-if="(post.imageUrl || post.mediaUrl) && isVideo(post.imageUrl || post.mediaUrl)" 
                :key="formatMediaUrl(post.imageUrl || post.mediaUrl)"
                controls 
                preload="metadata"
                style="width: 100%; border-radius: 12px; margin-top: 0.75rem;"
              >
                <source :src="formatMediaUrl(post.imageUrl || post.mediaUrl)" type="video/mp4">
                Browserul tău nu suportă redarea acestui video.
              </video>
              
              <section class="comments">
                <div v-if="comments[post.id] && comments[post.id].length">
                  <div v-for="comment in comments[post.id]" :key="comment.id" class="comment">
                    <b>@{{ comment.author?.username || comment.author }}</b> <small>{{ formatDate(comment.createdAt) }}</small>
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
        </div>

        <!-- COLOANA DREAPTA: Cardul Analytics -->
        <aside class="right-column">
          <div v-if="analyticsData" class="card analytics-card">
            <h2 style="margin-top: 0; margin-bottom: 1rem; font-size: 1.25rem;">📊 Analytics Overview</h2>

            <div style="display: flex; flex-direction: column; gap: 0.75rem; margin-bottom: 1.25rem;">
              <div style="background: #f9fafb; padding: 0.75rem 1rem; border-radius: 10px; border: 1px solid #e5e7eb;">
                <small style="color: #6b7280; font-weight: 600;">Like / Comment Ratio</small>
                <h3 style="margin: 0.25rem 0 0 0; font-size: 1.1rem;">{{ analyticsData.likeToCommentRatio }}</h3>
              </div>
              <div style="background: #f9fafb; padding: 0.75rem 1rem; border-radius: 10px; border: 1px solid #e5e7eb;">
                <small style="color: #6b7280; font-weight: 600;">Engagement Rank</small>
                <h3 style="margin: 0.25rem 0 0 0; font-size: 1.1rem;">Top {{ analyticsData.percentileRank }}%</h3>
              </div>
              <div style="background: #f9fafb; padding: 0.75rem 1rem; border-radius: 10px; border: 1px solid #e5e7eb;">
                <small style="color: #6b7280; font-weight: 600;">Avg Likes (Photo / Video)</small>
                <h3 style="margin: 0.25rem 0 0 0; font-size: 0.95rem;">
                  🖼️ {{ analyticsData.mediaStats?.avgPhotoLikes }} | 🎥 {{ analyticsData.mediaStats?.avgVideoLikes }}
                </h3>
              </div>
            </div>

            <h4 style="margin-bottom: 0.5rem; color: #374151; font-size: 0.95rem;">Weekly Activity</h4>
            <table style="width: 100%; border-collapse: collapse; margin-bottom: 1.25rem; font-size: 0.85rem;">
              <thead>
                <tr style="background: #f3f4f6; text-align: left;">
                  <th style="padding: 0.4rem;">Week</th>
                  <th style="padding: 0.4rem;">Posts</th>
                  <th style="padding: 0.4rem;">Likes</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="w in analyticsData.weeklyStats" :key="w.week" style="border-bottom: 1px solid #e5e7eb;">
                  <td style="padding: 0.4rem;">{{ w.week }}</td>
                  <td style="padding: 0.4rem;">{{ w.posts }}</td>
                  <td style="padding: 0.4rem;">{{ w.likes }}</td>
                </tr>
              </tbody>
            </table>

            <h4 style="margin-bottom: 0.5rem; color: #374151; font-size: 0.95rem;">Top 5 Engaged Posts</h4>
            <ol style="padding-left: 1rem; margin: 0; font-size: 0.8rem;">
              <li v-for="p in analyticsData.topPosts" :key="p.id" style="margin-bottom: 0.35rem;">
                "<strong>{{ p.content }}</strong>" — <em>{{ p.engagement }} interactions</em>
              </li>
            </ol>
          </div>
        </aside>
      </div>
    </main>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { createPost, fetchMe, listFeed, login, register as registerUser, toggleLike, listComments, addComment, getAnalytics } from './api'

const user = ref(null)
const posts = ref([])
const sort = ref('new')
const authMode = ref('login')
const error = ref('')
const authForm = reactive({ username: '', email: '', password: '', usernameOrEmail: '' })
const composer = reactive({ content: '', imageUrl: '' })

const uploadedMediaUrl = ref('')
const uploadStatus = ref('')
const uploadStatusColor = ref('gray')

const analyticsData = ref(null)

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
    await loadAnalytics()
  } catch (e) {
    user.value = null
    analyticsData.value = null
  }
}

async function loadAnalytics() {
  if (!user.value) return
  try {
    analyticsData.value = await getAnalytics(user.value.username)
  } catch (err) {
    console.error('Failed to load analytics:', err)
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
      uploadedMediaUrl.value = data.mediaUrl
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
  uploadedMediaUrl.value = ''
  composer.imageUrl = ''
  uploadStatus.value = ''
}

function isVideo(url) {
  if (!url) return false
  const cleanUrl = url.split('?')[0].toLowerCase()
  return cleanUrl.endsWith('.mp4') || cleanUrl.endsWith('.mov') || cleanUrl.endsWith('.webm')
}

function formatMediaUrl(url) {
  if (!url) return ''
  
  let fullUrl = url
  if (url.startsWith('/media/')) {
    fullUrl = 'http://localhost:8080' + url
    if (!isVideo(url)) {
      const separator = fullUrl.includes('?') ? '&' : '?'
      return `${fullUrl}${separator}t=${Date.now()}`
    }
  }

  return fullUrl
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
    await loadAnalytics()
  } catch (e) {
    error.value = e.response?.data?.detail || 'Authentication failed'
  }
}

async function loadFeed() {
  const responsePosts = await listFeed({ sort: sort.value })
  
  posts.value = responsePosts.map(post => ({
    ...post,
    imageUrl: post.imageUrl || post.mediaUrl,
    likeCount: post.likeCount ?? 0,
    commentCount: post.commentCount ?? 0,
    likedByCurrentUser: !!post.likedByCurrentUser
  }))

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
    const post = posts.value.find(p => p.id === postId)
    if (post) post.commentCount = (comments[postId]?.length || 0)
    await loadAnalytics()
  } catch (e) {
    commentError[postId] = e.response?.data?.detail || 'Failed to add comment'
  } finally {
    commentLoading[postId] = false
  }
}

async function createNewPost() {
  const text = composer.content ? composer.content.trim() : ''
  const media = uploadedMediaUrl.value || (composer.imageUrl ? composer.imageUrl.trim() : '')
  
  if (!text && !media) return

  try {
    const created = await createPost({ 
      content: text || '.', 
      imageUrl: media || null,
      mediaUrl: media || null
    })
    
    // Adăugăm postarea nouă la începutul listei pentru afișare instantă pe ecran
    const newPostObj = {
      ...(created || {}),
      id: created?.id || Date.now().toString(),
      content: text || '.',
      imageUrl: media || null,
      mediaUrl: media || null,
      likeCount: 0,
      commentCount: 0,
      createdAt: new Date().toISOString(),
      author: user.value
    }

    posts.value.unshift(newPostObj)
    
    composer.content = ''
    composer.imageUrl = ''
    uploadedMediaUrl.value = ''
    uploadStatus.value = ''
    
    // Actualizăm doar Analytics în fundal, fără a reîncărca feed-ul complet (să nu mai dispară postarea noua)
    await loadAnalytics()
  } catch (err) {
    console.error('Eroare la crearea postarii:', err)
  }
}

async function togglePostLike(post) {
  const result = await toggleLike(post.id)
  post.likedByCurrentUser = result.liked
  post.likeCount = result.likeCount
  await loadAnalytics()
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
  document.cookie = 'JSESSIONID=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;'
  document.cookie = 'ACCESS_TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;'
  user.value = null
  posts.value = []
  analyticsData.value = null
}
</script>

<style scoped>
.app-shell {
  max-width: 1200px;
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

.main-layout {
  display: flex;
  gap: 1.5rem;
  align-items: flex-start;
}

.left-column {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  min-width: 0;
}

.right-column {
  width: 320px;
  position: sticky;
  top: 1.5rem;
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

.feed-list {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.post header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
}

/* Restabilit la dimensiunea initiala de 300px */
.post img {
  margin-top: 0.75rem;
  width: 100%;
  border-radius: 12px;
  max-width: 300px;
  height: auto;
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