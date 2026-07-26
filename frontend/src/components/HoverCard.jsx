import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';

const GAP = 10;

/**
 * Trigger nhỏ + thẻ nổi hiện khi hover hoặc focus. Thẻ đi qua portal vì các chỗ dùng nó (ô ngày
 * trong lịch, hàng danh sách ở trang chủ) đều có `overflow: hidden` sẽ cắt mất popup.
 *
 * @param card     nội dung thẻ nổi
 * @param width    bề rộng thẻ, cần biết trước để quyết định lật sang trái khi sát mép phải
 * @param as       thẻ HTML dùng cho trigger
 */
export default function HoverCard({
  card,
  children,
  width = 240,
  as: Tag = 'span',
  className = '',
  activeClassName = 'is-previewing',
  cardClassName = 'plan-preview',
  title,
}) {
  const triggerRef = useRef(null);
  const cardRef = useRef(null);
  const closeTimer = useRef(null);
  const [pos, setPos] = useState(null);

  const cancelClose = useCallback(() => {
    clearTimeout(closeTimer.current);
  }, []);

  const open = useCallback(() => {
    cancelClose();
    const rect = triggerRef.current?.getBoundingClientRect();
    if (!rect) return;
    // Mặc định hiện bên phải trigger; sát mép phải màn hình thì lật sang trái.
    const right = rect.right + GAP;
    const left = right + width > window.innerWidth - GAP ? rect.left - width - GAP : right;
    setPos({ top: rect.top, left: Math.max(GAP, left) });
  }, [cancelClose, width]);

  // Trễ một nhịp để con trỏ kịp đi từ trigger sang thẻ mà thẻ không đóng.
  const scheduleClose = useCallback(() => {
    cancelClose();
    closeTimer.current = setTimeout(() => setPos(null), 150);
  }, [cancelClose]);

  useEffect(() => cancelClose, [cancelClose]);

  // Toạ độ là `fixed` nên cuộn trang sẽ làm thẻ lệch khỏi trigger — đóng luôn.
  useEffect(() => {
    if (!pos) return undefined;
    const close = () => setPos(null);
    window.addEventListener('scroll', close, true);
    window.addEventListener('resize', close);
    return () => {
      window.removeEventListener('scroll', close, true);
      window.removeEventListener('resize', close);
    };
  }, [pos]);

  // Kéo thẻ lên khi nó tràn khỏi đáy màn hình (chiều cao chỉ biết sau khi render).
  useLayoutEffect(() => {
    if (!pos || !cardRef.current) return;
    const height = cardRef.current.offsetHeight;
    const maxTop = window.innerHeight - height - GAP;
    const nextTop = Math.max(GAP, Math.min(pos.top, maxTop));
    if (Math.abs(nextTop - pos.top) > 1) {
      setPos((prev) => (prev ? { ...prev, top: nextTop } : prev));
    }
  }, [pos]);

  return (
    <>
      <Tag
        ref={triggerRef}
        className={[className, pos ? activeClassName : ''].filter(Boolean).join(' ')}
        title={title}
        tabIndex={0}
        onMouseEnter={open}
        onMouseLeave={scheduleClose}
        onFocus={open}
        onBlur={scheduleClose}
      >
        {children}
      </Tag>

      {pos &&
        createPortal(
          <div
            ref={cardRef}
            className={cardClassName}
            style={{ top: pos.top, left: pos.left, width }}
            onMouseEnter={cancelClose}
            onMouseLeave={scheduleClose}
            // Portal vẫn nổi bọt sự kiện theo cây React nên phải chặn,
            // không thì bấm vào thẻ sẽ mở luôn modal của phần tử bên dưới.
            onClick={(e) => e.stopPropagation()}
          >
            {card}
          </div>,
          document.body
        )}
    </>
  );
}
