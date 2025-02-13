import react from '@vitejs/plugin-react-swc'
import {defineConfig} from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // required for local Dev to get around CORS
    proxy: {
      '/api': {
        target: 'http://localhost:9022',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
  css: {
    modules: {
      generateScopedName: '[local]__[hash:base64:5]'
    }
  }
})
