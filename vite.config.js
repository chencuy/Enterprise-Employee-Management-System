import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue()],
  root: 'frontend',
  build: {
    emptyOutDir: true,
    outDir: '../src/main/resources/static/build',
    rollupOptions: {
      input: 'frontend/src/main.js',
      output: {
        entryFileNames: 'app/main.js',
        chunkFileNames: 'app/[name].js',
        assetFileNames: 'app/[name][extname]'
      }
    }
  },
  server: {
    proxy: {
      '/api': 'http://localhost:8080'
    }
  }
});
