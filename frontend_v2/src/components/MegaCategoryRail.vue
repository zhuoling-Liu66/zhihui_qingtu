<template>
  <div class="mega-rail" @mouseleave="activeKey = null">
    <aside class="category-rail card-surface">
      <button
        v-for="item in items"
        :key="item.key"
        type="button"
        class="category-rail__item"
        :class="{ 'category-rail__item--active': activeKey === item.key }"
        @mouseenter="activeKey = item.key"
        @focus="activeKey = item.key"
        @click="handleLink({ type: 'section', slug: item.slug })"
      >
        <strong>{{ item.label }}</strong>
      </button>
    </aside>

    <transition name="fade-slide">
      <section v-if="activePanel" class="mega-rail__panel card-surface">
        <div v-for="section in activePanel.sections" :key="section.title" class="mega-rail__row">
          <h4>{{ section.title }}</h4>
          <div class="mega-rail__links">
            <button
              v-for="link in section.links"
              :key="`${section.title}-${link.label}`"
              type="button"
              @click="handleLink(link)"
            >
              {{ link.label }}
            </button>
          </div>
        </div>
      </section>
    </transition>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

const props = defineProps({
  items: {
    type: Array,
    required: true,
  },
})

const router = useRouter()
const activeKey = ref(null)

const activePanel = computed(() => props.items.find((item) => item.key === activeKey.value))

const handleLink = (link) => {
  if (link.type === 'search') {
    router.push({ name: 'search', query: { q: link.keyword } })
    return
  }

  if (link.type === 'section') {
    router.push({ name: 'section', params: { slug: link.slug } })
  }
}
</script>
