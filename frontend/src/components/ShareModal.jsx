import { useEffect, useState } from 'react';
import * as sharesApi from '../api/shares';
import { Badge, Button, ErrorBanner, Loading, Modal } from './ui';
import { useConfirm } from '../context/ConfirmContext';

/**
 * Tạo (hoặc lấy lại) mã chia sẻ cho một outfit / món đồ và cho phép sao chép, thu hồi.
 * `targetType` là 'OUTFIT' hoặc 'CLOTHING_ITEM'.
 */
export default function ShareModal({ targetType, targetId, targetName, onClose, onToast }) {
  const confirm = useConfirm();

  const [share, setShare] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [copied, setCopied] = useState(false);

  const create = () => {
    setBusy(true);
    setError(null);
    sharesApi
      .createShare(targetType, targetId)
      .then(setShare)
      .catch((err) => setError(err.message))
      .finally(() => setBusy(false));
  };

  // Mở modal là tạo mã luôn; nếu trang phục đã có mã còn hiệu lực thì server trả lại mã cũ.
  useEffect(create, [targetType, targetId]);

  const copyCode = async () => {
    try {
      await navigator.clipboard.writeText(share.code);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      setError('Trình duyệt không cho phép sao chép tự động. Hãy bôi đen mã và copy thủ công.');
    }
  };

  const handleRevoke = async () => {
    const ok = await confirm({
      title: 'Thu hồi mã chia sẻ?',
      message:
        'Người khác sẽ không dùng được mã này nữa. Những bản sao đã chép về tủ đồ của họ vẫn giữ nguyên.',
      confirmLabel: '🚫 Thu hồi',
      danger: true,
    });
    if (!ok) return;

    setBusy(true);
    try {
      await sharesApi.revokeShare(share.code);
      onToast?.('Đã thu hồi mã chia sẻ.');
      onClose();
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  };

  return (
    <Modal title="🔗 Chia sẻ" onClose={onClose}>
      <ErrorBanner error={error} onDismiss={() => setError(null)} />

      <p style={{ fontWeight: 700, marginBottom: 14 }}>
        {targetType === 'OUTFIT' ? '🧢' : '👕'} {targetName}
      </p>

      {!share ? (
        busy ? (
          <Loading>Đang tạo mã chia sẻ...</Loading>
        ) : (
          <Button variant="primary" onClick={create}>🔗 Tạo mã chia sẻ</Button>
        )
      ) : (
        <>
          <div className="share-code-box">
            <span className="share-code">{share.code}</span>
            <Button size="sm" onClick={copyCode}>
              {copied ? '✅ Đã chép' : '📋 Chép mã'}
            </Button>
          </div>

          <p className="auth-hint">
            Gửi mã này cho bạn bè. Họ đăng nhập WearWise, mở trang <strong>Chia sẻ</strong>, nhập mã
            và bấm thêm — một bản sao sẽ nằm trong tủ đồ của họ. Bản sao đó độc lập hoàn toàn, bạn
            sửa hay xóa đồ của mình cũng không ảnh hưởng tới họ.
          </p>

          <div className="badge-row" style={{ marginTop: 12 }}>
            <Badge color="purple">📥 {share.importCount} lượt thêm</Badge>
            {share.expiresAt ? <Badge color="orange">⏳ Có hạn</Badge> : <Badge color="green">♾️ Không hết hạn</Badge>}
          </div>

          <div className="nb-modal-actions">
            <Button variant="danger" onClick={handleRevoke} disabled={busy}>
              🚫 Thu hồi mã
            </Button>
            <Button variant="primary" onClick={onClose}>Xong</Button>
          </div>
        </>
      )}
    </Modal>
  );
}
