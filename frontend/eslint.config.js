import js from '@eslint/js';
import globals from 'globals';
import react from 'eslint-plugin-react';
import reactHooks from 'eslint-plugin-react-hooks';

/**
 * Cấu hình ESLint cố tình hẹp: chỉ những luật bắt được **lỗi thật**, không có luật về phong cách.
 *
 * Lý do có file này: `vite build` không kiểm tra biến có tồn tại hay không. Một lần đổi tên biến
 * còn sót chỗ dùng đã lọt qua build và chỉ vỡ ra lúc chạy — đúng nhánh giao diện hiếm khi hiển
 * thị — thành màn hình trắng. `no-undef` bắt đúng loại lỗi đó trong chưa tới một giây.
 *
 * Không thêm luật định dạng (dấu chấm phẩy, độ dài dòng...): chúng tạo ra nhiễu và làm người ta
 * quen tay bỏ qua cảnh báo, rồi bỏ qua luôn cả cảnh báo thật.
 */
export default [
  {
    ignores: ['dist/**', 'node_modules/**'],
  },
  js.configs.recommended,
  {
    files: ['**/*.{js,jsx}'],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'module',
      globals: {
        ...globals.browser,
        ...globals.es2021,
      },
      parserOptions: {
        ecmaFeatures: { jsx: true },
      },
    },
    settings: {
      react: { version: 'detect' },
    },
    plugins: {
      react,
      'react-hooks': reactHooks,
    },
    rules: {
      // Cần plugin react thì `no-unused-vars` mới hiểu <Button /> là có dùng Button; thiếu nó
      // thì mọi component import vào đều bị báo thừa và cảnh báo trở nên vô dụng vì quá nhiễu.
      'react/jsx-uses-vars': 'error',
      'react/jsx-uses-react': 'error',

      // Lỗi nguy hiểm nhất trong dự án không có TypeScript: dùng biến không tồn tại.
      'no-undef': 'error',

      // Biến thừa thường là dấu vết của một lần refactor làm dở.
      'no-unused-vars': ['warn', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],

      // Thiếu dependency trong useEffect sinh ra closure cũ — loại lỗi cực khó lần ra.
      'react-hooks/rules-of-hooks': 'error',
      'react-hooks/exhaustive-deps': 'warn',
    },
  },
];
