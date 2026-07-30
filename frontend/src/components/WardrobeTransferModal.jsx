import { useRef, useState } from 'react';
import * as wardrobeApi from '../api/wardrobe';
import { Button, ErrorBanner, Modal } from './ui';

/**
 * Nhập / xuất tủ đồ bằng CSV.
 *
 * <p>Ba khối theo đúng thứ tự người dùng cần: tải file mẫu → nộp file → (hoặc) xuất bản sao. Nút
 * "Tải file mẫu" đứng trước cả nút nhập vì nhập mà không có mẫu thì hầu như chắc chắn sai cột.</p>
 */
export default function WardrobeTransferModal({ onImported, onClose }) {
  const fileInput = useRef(null);
  const [file, setFile] = useState(null);
  const [skipDuplicateNames, setSkipDuplicateNames] = useState(true);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(null);

  const run = async (key, action) => {
    setBusy(key);
    setError(null);
    try {
      return await action();
    } catch (err) {
      setError(err.message);
      return null;
    } finally {
      setBusy(null);
    }
  };

  const handleImport = async () => {
    if (!file) {
      setError('Hãy chọn file CSV cần nhập.');
      return;
    }

    const response = await run('import', () => wardrobeApi.importWardrobe(file, { skipDuplicateNames }));
    if (!response) return;

    setResult(response);
    setFile(null);
    if (fileInput.current) fileInput.current.value = '';
    // Có món mới thì danh sách phía sau phải tải lại, nhưng modal vẫn mở để người dùng đọc báo cáo.
    if (response.created > 0) await onImported(response);
  };

  return (
    <Modal title="📁 Nhập / xuất tủ đồ" wide onClose={onClose}>
      <ErrorBanner error={error} onDismiss={() => setError(null)} />

      <div className="transfer-grid">
        <section className="transfer-block">
          <h4 className="transfer-title">1. Lấy file mẫu</h4>
          <p className="transfer-hint">
            File CSV có sẵn dòng tiêu đề, hai dòng ví dụ và ghi chú giá trị hợp lệ của từng cột. Mở
            bằng Excel hoặc Google Sheets, xóa hai dòng ví dụ rồi điền tủ đồ của bạn.
          </p>
          <Button
            variant="blue"
            disabled={busy !== null}
            onClick={() => run('template', wardrobeApi.downloadTemplate)}
          >
            {busy === 'template' ? 'Đang tải...' : '📄 Tải file mẫu'}
          </Button>
        </section>

        <section className="transfer-block">
          <h4 className="transfer-title">2. Xuất tủ đồ hiện có</h4>
          <p className="transfer-hint">
            Toàn bộ món chưa bị ẩn, đúng định dạng của file mẫu — nên bản xuất ra cũng nhập lại được.
            Dùng để sao lưu, hoặc để sửa hàng loạt rồi nhập lại.
          </p>
          <Button
            variant="green"
            disabled={busy !== null}
            onClick={() => run('export', wardrobeApi.exportWardrobe)}
          >
            {busy === 'export' ? 'Đang xuất...' : '⬇️ Xuất ra CSV'}
          </Button>
        </section>
      </div>

      <section className="transfer-block transfer-block--import">
        <h4 className="transfer-title">3. Nhập từ file CSV</h4>

        <input
          ref={fileInput}
          type="file"
          accept=".csv,text/csv"
          className="nb-input transfer-file"
          onChange={(e) => {
            setFile(e.target.files?.[0] || null);
            setResult(null);
          }}
        />

        <label className="transfer-check">
          <input
            type="checkbox"
            checked={skipDuplicateNames}
            onChange={(e) => setSkipDuplicateNames(e.target.checked)}
          />
          <span>
            Bỏ qua món đã có tên trong tủ.{' '}
            <strong>Bỏ tick là cho phép trùng tên</strong> — nhập lại đúng file cũ sẽ nhân đôi tủ đồ.
          </span>
        </label>

        <p className="transfer-hint">
          Tối đa 500 dòng mỗi lần. Dòng sai được báo kèm số dòng, những dòng còn lại vẫn vào tủ —
          sửa file rồi nộp lại, phần đã vào sẽ tự bỏ qua. Ảnh không nhập bằng file này.
        </p>

        <Button variant="primary" disabled={busy !== null || !file} onClick={handleImport}>
          {busy === 'import' ? 'Đang nhập...' : '⬆️ Nhập vào tủ đồ'}
        </Button>
      </section>

      {result && (
        <section className="transfer-result">
          <div className="transfer-tally">
            <span className="transfer-pill transfer-pill--ok">Đã thêm {result.created}</span>
            <span className="transfer-pill">Bỏ qua {result.skipped}</span>
            <span className={`transfer-pill ${result.failed > 0 ? 'transfer-pill--bad' : ''}`}>
              Lỗi {result.failed}
            </span>
            <span className="transfer-hint" style={{ margin: 0 }}>
              trên {result.totalRows} dòng
            </span>
          </div>

          {result.problems.length === 0 ? (
            <p className="transfer-hint" style={{ marginBottom: 0 }}>
              Không có dòng nào cần xem lại.
            </p>
          ) : (
            <div className="transfer-problems">
              {result.problems.map((problem) => (
                <div
                  key={`${problem.line}-${problem.kind}`}
                  className={`transfer-problem ${problem.kind === 'INVALID' ? 'is-bad' : ''}`}
                >
                  <span className="transfer-problem-line">Dòng {problem.line}</span>
                  <span className="transfer-problem-name" title={problem.name}>
                    {problem.name || '(chưa có tên)'}
                  </span>
                  <span className="transfer-problem-msg">{problem.message}</span>
                </div>
              ))}
            </div>
          )}
        </section>
      )}

      <div className="nb-modal-actions">
        <Button onClick={onClose} disabled={busy !== null}>
          {result ? 'Xong' : 'Đóng'}
        </Button>
      </div>
    </Modal>
  );
}
