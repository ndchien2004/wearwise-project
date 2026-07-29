import { useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';

/**
 * Đưa số trang và bộ lọc lên thanh địa chỉ thay vì giữ trong state của component.
 *
 * Được gì:
 *  - tải lại trang vẫn ở nguyên chỗ cũ, thay vì nhảy về trang 1;
 *  - nút Back/Forward của trình duyệt hoạt động đúng;
 *  - dán link cho người khác thì họ thấy đúng cái mình đang thấy (cả trang lẫn bộ lọc).
 * Giữ trong state thì mất cả ba.
 *
 * Quy ước:
 *  - số trang trên URL đếm từ **1** cho khớp với thứ người dùng nhìn thấy, còn API dùng chỉ số từ
 *    0 theo quy ước của Spring Data — quy đổi gói gọn ở đây để nơi khác khỏi phải nhớ;
 *  - trang 1 và giá trị rỗng không được ghi vào URL: `?page=1&keyword=` chỉ là nhiễu.
 */
export function usePagedParams() {
  const [searchParams, setSearchParams] = useSearchParams();

  const page = Math.max(0, (Number(searchParams.get('page')) || 1) - 1);

  const updateParams = useCallback(
    (changes) => {
      setSearchParams(
        (previous) => {
          const next = new URLSearchParams(previous);
          Object.entries(changes).forEach(([key, value]) => {
            if (value === null || value === undefined || value === '') {
              next.delete(key);
            } else {
              next.set(key, String(value));
            }
          });
          return next;
        },
        // replace: đổi trang không nên tạo một mục lịch sử mới cho mỗi lần bấm, nếu không
        // bấm Back mười lần mới thoát được khỏi danh sách.
        { replace: true }
      );
    },
    [setSearchParams]
  );

  const setPage = useCallback(
    (nextPage) => updateParams({ page: nextPage <= 0 ? null : nextPage + 1 }),
    [updateParams]
  );

  return { searchParams, page, setPage, updateParams };
}
