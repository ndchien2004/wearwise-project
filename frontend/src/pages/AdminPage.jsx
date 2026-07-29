import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import * as adminApi from '../api/admin';
import {
  LockAccountModal,
  RateLimitModal,
  UnlockAccountModal,
} from '../components/AdminUserModals';
import BarChart from '../components/BarChart';
import { Badge, Button, EmptyState, ErrorBanner, Loading, Pagination, Toast } from '../components/ui';
import { useAuth } from '../context/AuthContext';
import { formatDateTime, formatDateTimeFull } from '../utils/date';
import { usePagedParams } from '../utils/usePagedParams';
import {
  AUDIT_ACTION_BADGES,
  AUDIT_ACTION_EMOJIS,
  AUDIT_ACTION_LABELS,
  label,
} from '../utils/labels';

const TABS = [
  { key: 'overview', to: '/admin', emoji: '📊', label: 'Tổng quan' },
  { key: 'users', to: '/admin/users', emoji: '👥', label: 'Tài khoản' },
  { key: 'audit', to: '/admin/audit', emoji: '📜', label: 'Nhật ký' },
];

const USERS_PAGE_SIZE = 20;
const AUDIT_PAGE_SIZE = 25;


/** Màu biểu đồ theo mức độ đáng chú ý — trùng logic với badge ở danh sách nhật ký. */
const AUDIT_CHART_COLORS = {
  LOGIN_SUCCEEDED: '#2f9e44',
  LOGIN_FAILED: '#e8590c',
  ACCOUNT_AUTO_LOCKED: '#c92a2a',
  REFRESH_TOKEN_REUSE_DETECTED: '#c92a2a',
  ADMIN_LOCKED_ACCOUNT: '#7048e8',
  ADMIN_UNLOCKED_ACCOUNT: '#7048e8',
  ADMIN_CHANGED_RATE_LIMIT: '#7048e8',
};
const AUDIT_CHART_DEFAULT_COLOR = '#1971c2';

export default function AdminPage() {
  // Không có :tab trên URL nghĩa là đang ở /admin — mặc định về Tổng quan.
  const { tab = 'overview' } = useParams();

  return (
    <div>
      <div className="page-header">
        <div className="page-heading-group">
          <h1 className="page-title">🛡️ Quản trị</h1>
        </div>

        <div className="page-switch" role="tablist" aria-label="Khu vực quản trị">
          {TABS.map((item) => (
            <Link
              key={item.key}
              to={item.to}
              role="tab"
              aria-selected={tab === item.key}
              className={`page-switch-btn ${tab === item.key ? 'is-active' : ''}`}
            >
              <span className="page-switch-emoji">{item.emoji}</span>
              {item.label}
            </Link>
          ))}
        </div>
      </div>

      {tab === 'users' ? <UsersTab /> : tab === 'audit' ? <AuditTab /> : <OverviewTab />}
    </div>
  );
}

/* ============================ Tổng quan ============================ */

function OverviewTab() {
  const [overview, setOverview] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    adminApi
      .getOverview()
      .then((data) => {
        if (!cancelled) setOverview(data);
      })
      .catch((err) => {
        if (!cancelled) setError(err.message);
      });

    return () => {
      cancelled = true;
    };
  }, []);

  if (error) return <ErrorBanner error={error} />;
  if (!overview) return <Loading>Đang tổng hợp số liệu...</Loading>;

  const auditRows = Object.entries(overview.auditCountsLast7Days || {})
    .filter(([, value]) => value > 0)
    .sort((a, b) => b[1] - a[1])
    .map(([key, value]) => ({
      key,
      label: label(AUDIT_ACTION_LABELS, key),
      value,
      color: AUDIT_CHART_COLORS[key] || AUDIT_CHART_DEFAULT_COLOR,
    }));

  return (
    <>
      <div className="stat-row stat-row--compact">
        <div className="stat-tile" style={{ background: 'var(--yellow)' }}>
          <div className="stat-value">👥 {overview.usersTotal}</div>
          <div className="stat-label">Tổng người dùng hiện tại</div>
        </div>
        {/* Nền đỏ chỉ khi thật sự có tài khoản bị khóa — không để ô này lúc nào cũng đỏ,
            bằng không màu cảnh báo mất hết ý nghĩa. */}
        <div className="stat-tile" style={{ background: overview.usersLocked > 0 ? 'var(--red)' : 'var(--paper)' }}>
          <div className="stat-value">🔒 {overview.usersLocked}</div>
          <div className="stat-label">Tài khoản đang bị khóa</div>
        </div>
      </div>

      <div className="nb-card chart-section">
        <h3 className="chart-title">📜 Sự kiện kiểm toán trong 7 ngày</h3>
        {auditRows.length === 0 ? (
          <EmptyState emoji="🌤️">Chưa có sự kiện nào được ghi nhận trong tuần qua.</EmptyState>
        ) : (
          <BarChart data={auditRows} />
        )}
      </div>
    </>
  );
}

/* ============================ Tài khoản ============================ */

function UsersTab() {
  const { username: currentUsername } = useAuth();
  const { searchParams, page, setPage, updateParams } = usePagedParams();
  const query = searchParams.get('q') ?? '';

  // Ô nhập giữ state riêng vì nó đổi theo từng phím gõ; chỉ khi bấm Tìm mới đẩy lên URL.
  const [search, setSearch] = useState(query);
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [toast, setToast] = useState(null);
  const [lockTarget, setLockTarget] = useState(null);
  const [unlockTarget, setUnlockTarget] = useState(null);
  const [quotaTarget, setQuotaTarget] = useState(null);

  // Bấm Back/Forward hoặc mở link có sẵn ?q= thì ô nhập phải khớp lại với URL.
  useEffect(() => setSearch(query), [query]);

  const load = useCallback(() => {
    adminApi
      .listUsers({ query, page, size: USERS_PAGE_SIZE })
      .then(setData)
      .catch((err) => setError(err.message));
  }, [query, page]);

  useEffect(load, [load]);

  // Gõ xong mới tìm: tránh bắn một request cho mỗi ký tự. Đổi từ khóa thì về trang 1 —
  // giữ nguyên trang 5 với kết quả mới là gần như chắc chắn ra danh sách rỗng.
  const applySearch = (event) => {
    event.preventDefault();
    updateParams({ q: search.trim() || null, page: null });
  };

  const afterAction = (message) => {
    setToast(message);
    setLockTarget(null);
    setUnlockTarget(null);
    setQuotaTarget(null);
    load();
  };

  if (error) return <ErrorBanner error={error} onDismiss={() => setError(null)} />;
  if (!data) return <Loading>Đang tải danh sách tài khoản...</Loading>;

  const users = data.content || [];

  return (
    <>
      <Toast message={toast} onDismiss={() => setToast(null)} />

      <form className="filter-bar" onSubmit={applySearch}>
        <div className="admin-search-row">
          <input
            className="nb-input"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Tìm theo tên đăng nhập hoặc email..."
            aria-label="Tìm tài khoản"
          />
          <Button type="submit" variant="primary">
            🔍 Tìm
          </Button>
          {query && (
            <Button onClick={() => updateParams({ q: null, page: null })}>Xóa lọc</Button>
          )}
        </div>
      </form>

      {users.length === 0 ? (
        <EmptyState emoji="🔍">Không có tài khoản nào khớp với từ khóa.</EmptyState>
      ) : (
        <div className="stack-list">
          {users.map((user) => (
            <UserRow
              key={user.id}
              user={user}
              isSelf={user.username === currentUsername}
              onLock={() => setLockTarget(user)}
              onUnlock={() => setUnlockTarget(user)}
              onQuota={() => setQuotaTarget(user)}
            />
          ))}
        </div>
      )}

      <Pagination page={page} pageCount={data.totalPages || 1} onChange={setPage} />

      {lockTarget && (
        <LockAccountModal
          username={lockTarget.username}
          onClose={() => setLockTarget(null)}
          onSubmit={async (payload) => {
            await adminApi.lockUser(lockTarget.username, payload);
            afterAction(`Đã khóa tài khoản ${lockTarget.username}.`);
          }}
        />
      )}

      {unlockTarget && (
        <UnlockAccountModal
          username={unlockTarget.username}
          onClose={() => setUnlockTarget(null)}
          onSubmit={async (payload) => {
            await adminApi.unlockUser(unlockTarget.username, payload);
            afterAction(`Đã mở khóa tài khoản ${unlockTarget.username}.`);
          }}
        />
      )}

      {quotaTarget && (
        <RateLimitModal
          user={quotaTarget}
          onClose={() => setQuotaTarget(null)}
          onSubmit={async (payload) => {
            await adminApi.setRateLimit(quotaTarget.username, payload);
            afterAction(`Đã cập nhật hạn mức của ${quotaTarget.username}.`);
          }}
        />
      )}
    </>
  );
}

function UserRow({ user, isSelf, onLock, onUnlock, onQuota }) {
  const isAdmin = user.role === 'ADMIN';
  // Cả hai trường hợp này server đều từ chối; khóa nút ở đây để khỏi bắt người dùng
  // mở hộp thoại, gõ lý do rồi mới biết là không được.
  const cannotLock = isSelf || isAdmin;
  const hasQuota = user.aiQuotaPerHour !== null || user.externalQuotaPerHour !== null;

  return (
    <div className={`admin-row ${user.locked ? 'is-locked' : ''}`}>
      <span className={`admin-row-avatar ${isAdmin ? 'is-admin' : ''}`} aria-hidden="true">
        {isAdmin ? '🛡️' : user.username.charAt(0).toUpperCase()}
      </span>

      <div className="admin-row-main">
        <div className="admin-row-name">
          {user.username}
          {isSelf && <Badge color="muted">Bạn</Badge>}
          {isAdmin && <Badge color="purple">Quản trị</Badge>}
          {user.locked && <Badge color="red">🔒 Đang khóa</Badge>}
        </div>

        <div className="admin-row-meta">
          <span>✉️ {user.email || 'Chưa có email'}</span>
          <span>📅 Tạo {formatDateTime(user.createdAt)}</span>
          {user.locked && <span>⏳ Tới {formatDateTimeFull(user.lockedUntil)}</span>}
          {user.failedLoginAttempts > 0 && <span>⚠️ {user.failedLoginAttempts} lần sai liên tiếp</span>}
          {hasQuota && (
            <span>
              🎚️ AI {user.aiQuotaPerHour ?? 'mặc định'} · Ngoài {user.externalQuotaPerHour ?? 'mặc định'}
            </span>
          )}
        </div>
      </div>

      <div className="admin-row-actions">
        <Button size="sm" onClick={onQuota}>
          🎚️ Hạn mức
        </Button>
        {user.locked ? (
          <Button size="sm" variant="green" onClick={onUnlock}>
            🔓 Mở khóa
          </Button>
        ) : (
          <Button
            size="sm"
            variant="danger"
            onClick={onLock}
            disabled={cannotLock}
            title={
              isSelf
                ? 'Không thể tự khóa tài khoản của chính mình'
                : isAdmin
                  ? 'Không khóa được tài khoản quản trị qua giao diện — phải hạ quyền trong database trước'
                  : undefined
            }
          >
            🔒 Khóa
          </Button>
        )}
      </div>
    </div>
  );
}

/* ============================ Nhật ký ============================ */

/** Sự kiện đẩy về có khớp bộ lọc đang bật hay không — quyết định chèn thẳng hay chỉ đếm. */
function matchesFilter(event, action, username) {
  if (action && event.action !== action) return false;
  if (username) {
    const needle = username.toLowerCase();
    const actor = (event.actorUsername || '').toLowerCase();
    const target = (event.targetUsername || '').toLowerCase();
    if (actor !== needle && target !== needle) return false;
  }
  return true;
}

function AuditTab() {
  const { searchParams, page, setPage, updateParams } = usePagedParams();
  const action = searchParams.get('action') ?? '';
  const appliedUsername = searchParams.get('username') ?? '';

  const [usernameFilter, setUsernameFilter] = useState(appliedUsername);
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [live, setLive] = useState(false);
  /** Số sự kiện mới nhận được nhưng chưa hiện ra (đang ở trang khác hoặc không khớp bộ lọc). */
  const [pendingCount, setPendingCount] = useState(0);
  /** Id các sự kiện vừa được đẩy về — dùng để làm nổi bật vài giây rồi thôi. */
  const [freshIds, setFreshIds] = useState(() => new Set());

  // Đếm số lần gọi để bỏ qua phản hồi về muộn: đổi bộ lọc nhanh có thể khiến kết quả của lần
  // gọi cũ về sau lần mới và ghi đè lên dữ liệu đúng.
  const requestId = useRef(0);

  const reload = useCallback(() => {
    const id = ++requestId.current;
    setPendingCount(0);
    return adminApi
      .listAuditEvents({ action, username: appliedUsername, page, size: AUDIT_PAGE_SIZE })
      .then((result) => {
        if (id === requestId.current) setData(result);
      })
      .catch((err) => {
        if (id === requestId.current) setError(err.message);
      });
  }, [action, appliedUsername, page]);

  useEffect(() => {
    reload();
  }, [reload]);

  // Bộ lọc, trang hiện tại và hàm tải lại đều đọc qua ref: kênh SSE chỉ mở MỘT lần và phải sống
  // xuyên suốt, không được nối lại mỗi khi người dùng đổi bộ lọc. Nếu đọc thẳng biến trong
  // closure thì effect sẽ mãi dùng giá trị của lần render đầu tiên.
  const filterRef = useRef({ action, appliedUsername, page });
  filterRef.current = { action, appliedUsername, page };

  const reloadRef = useRef(reload);
  reloadRef.current = reload;

  useEffect(() => {
    const controller = new AbortController();
    let stopped = false;
    let retryTimer = null;

    const handleEvent = (event) => {
      const { action: currentAction, appliedUsername: currentUsername, page: currentPage } =
        filterRef.current;

      // Chỉ chèn thẳng khi đang xem trang đầu và sự kiện khớp bộ lọc. Ở trang 2 trở đi mà chèn
      // vào thì thứ tự phân trang sẽ loạn, nên chỉ đếm rồi để người dùng tự làm mới.
      if (currentPage !== 0 || !matchesFilter(event, currentAction, currentUsername)) {
        setPendingCount((count) => count + 1);
        return;
      }

      setData((previous) => {
        if (!previous) return previous;
        const content = [event, ...previous.content].slice(0, AUDIT_PAGE_SIZE);
        return {
          ...previous,
          content,
          totalElements: (previous.totalElements ?? content.length) + 1,
        };
      });

      setFreshIds((previous) => new Set(previous).add(event.id));
      setTimeout(() => {
        setFreshIds((previous) => {
          const next = new Set(previous);
          next.delete(event.id);
          return next;
        });
      }, 6000);
    };

    const run = async () => {
      while (!stopped) {
        try {
          await adminApi.streamAuditEvents({
            onEvent: handleEvent,
            onOpen: () => setLive(true),
            signal: controller.signal,
          });
        } catch {
          // Nuốt lỗi: mất kết nối là chuyện bình thường (ngủ máy, đổi mạng, proxy cắt).
        }

        if (stopped) return;
        setLive(false);

        // Chờ rồi nối lại. Sự kiện phát sinh trong lúc mất kết nối không được đẩy lại, nên
        // phải tải lại danh sách — kênh SSE chỉ là thông báo, nguồn thật là REST.
        await new Promise((resolve) => {
          retryTimer = setTimeout(resolve, 3000);
        });
        if (!stopped) reloadRef.current();
      }
    };

    run();

    return () => {
      stopped = true;
      controller.abort();
      if (retryTimer) clearTimeout(retryTimer);
    };
    // Chủ ý chỉ chạy một lần: bộ lọc và hàm tải lại đọc qua ref, nên kênh SSE không bị nối lại
    // mỗi khi người dùng đổi bộ lọc.
  }, []);

  // Back/Forward hoặc mở link có sẵn ?username= thì ô nhập phải khớp lại với URL.
  useEffect(() => setUsernameFilter(appliedUsername), [appliedUsername]);

  const applyUsername = (event) => {
    event.preventDefault();
    updateParams({ username: usernameFilter.trim() || null, page: null });
  };

  if (error) return <ErrorBanner error={error} onDismiss={() => setError(null)} />;

  const events = data?.content || [];

  return (
    <>
      <form className="filter-bar" onSubmit={applyUsername}>
        <div className="admin-search-row">
          <select
            className="nb-select"
            value={action}
            onChange={(e) => updateParams({ action: e.target.value || null, page: null })}
            aria-label="Lọc theo loại sự kiện"
          >
            <option value="">Tất cả loại sự kiện</option>
            {Object.keys(AUDIT_ACTION_LABELS).map((key) => (
              <option key={key} value={key}>
                {AUDIT_ACTION_LABELS[key]}
              </option>
            ))}
          </select>

          <input
            className="nb-input"
            value={usernameFilter}
            onChange={(e) => setUsernameFilter(e.target.value)}
            placeholder="Lọc theo tài khoản..."
            aria-label="Lọc theo tài khoản"
          />
          <Button type="submit" variant="primary">
            🔍 Lọc
          </Button>
          {(action || appliedUsername) && (
            <Button onClick={() => updateParams({ action: null, username: null, page: null })}>
              Xóa lọc
            </Button>
          )}
        </div>
      </form>

      <div className="audit-status-bar">
        <span className={`live-dot ${live ? 'is-live' : ''}`} aria-hidden="true" />
        <span className="audit-status-text">
          {live
            ? 'Đang theo dõi trực tiếp — sự kiện mới hiện ngay, không cần tải lại trang.'
            : 'Mất kết nối tới kênh trực tiếp. Đang thử nối lại...'}
        </span>

        {pendingCount > 0 && (
          <Button size="sm" variant="primary" onClick={reload}>
            ↻ {pendingCount} sự kiện mới
          </Button>
        )}
      </div>

      {!data ? (
        <Loading>Đang tải nhật ký...</Loading>
      ) : events.length === 0 ? (
        <EmptyState emoji="📭">Chưa có sự kiện nào khớp với bộ lọc.</EmptyState>
      ) : (
        <div className="stack-list">
          {events.map((event) => (
            <AuditRow key={event.id} event={event} isFresh={freshIds.has(event.id)} />
          ))}
        </div>
      )}

      <Pagination page={page} pageCount={data?.totalPages || 1} onChange={setPage} />
    </>
  );
}

function AuditRow({ event, isFresh }) {
  // Chỉ hiện "→ tài khoản" khi người gây ra khác người bị tác động, tức là hành động của
  // quản trị viên. Sự kiện tự thực hiện thì hai trường trùng nhau, hiện cả hai là thừa.
  const showTarget = event.targetUsername && event.targetUsername !== event.actorUsername;
  const badgeClass = AUDIT_ACTION_BADGES[event.action] || 'nb-badge--muted';

  return (
    <div className={`admin-row admin-row--audit ${isFresh ? 'is-fresh' : ''}`}>
      <span className="admin-row-avatar" aria-hidden="true">
        {AUDIT_ACTION_EMOJIS[event.action] || '•'}
      </span>

      <div className="admin-row-main">
        <div className="admin-row-name">
          <span className={`nb-badge ${badgeClass}`}>{label(AUDIT_ACTION_LABELS, event.action)}</span>
          <span className="admin-row-actor">{event.actorUsername || '—'}</span>
          {showTarget && <span className="admin-row-arrow">→ {event.targetUsername}</span>}
        </div>

        {event.detail && <p className="admin-row-detail">{event.detail}</p>}

        <div className="admin-row-meta">
          <span>🕐 {formatDateTimeFull(event.occurredAt)}</span>
          {event.ipAddress && <span>🌐 {event.ipAddress}</span>}
          {event.userAgent && (
            <span className="admin-row-agent" title={event.userAgent}>
              💻 {event.userAgent}
            </span>
          )}
        </div>
      </div>
    </div>
  );
}
