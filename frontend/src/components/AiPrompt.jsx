import { Button } from './ui';

/**
 * Chỗ đứng của một tab AI chưa chạy lần nào: nói rõ nó sẽ làm gì rồi mới mời bấm.
 *
 * Nằm gọn trong vùng kết quả nên không đẩy danh sách outfit xuống dưới màn hình — khác với cách cũ
 * là một thẻ rỗng chờ sẵn ở đầu trang, vẫn chiếm chỗ dù chưa có gì để hiện.
 */
export default function AiPrompt({ emoji, title, hint, actionLabel, loading, loadingLabel, onRun }) {
  return (
    <div className="ai-prompt">
      <span className="ai-prompt-emoji">{emoji}</span>
      <h3 className="ai-prompt-title">{title}</h3>
      <p className="ai-prompt-hint">{hint}</p>
      <Button variant="primary" onClick={onRun} disabled={loading}>
        {loading ? loadingLabel : actionLabel}
      </Button>
    </div>
  );
}
