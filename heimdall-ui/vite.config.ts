import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  // the dashboard is served by the host application at /heimdall/ (heimdall-core
  // packages dist/ into META-INF/resources/heimdall), so every asset URL needs it
  base: '/heimdall/',
  server: {
    // only the backend routes go to Quarkus — the UI itself is served by Vite
    // (now also under /heimdall/), so proxying all of /heimdall would hide it
    proxy: {
      '/heimdall/api': {
        target: 'http://localhost:8080',
      },
      '/heimdall/ws': {
        target: 'http://localhost:8080',
        ws: true,
      },
    },
  },
})
